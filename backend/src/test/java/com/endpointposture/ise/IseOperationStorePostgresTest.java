package com.endpointposture.ise;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.endpointposture.audit.IseActionAuditRepository;
import com.endpointposture.endpoint.Endpoint;
import com.endpointposture.endpoint.EndpointRepository;
import com.endpointposture.posture.AssessmentService;
import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class IseOperationStorePostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("ise_test").withUsername("test").withPassword("test");
    static JdbcTemplate jdbc;
    static IseOperationStore store;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(ds);
        store = new IseOperationStore(jdbc, new DataSourceTransactionManager(ds), 2, 300000, 30, 900);
    }

    private UUID endpoint() {
        return jdbc.queryForObject("INSERT INTO endpoint(mac_address) VALUES (?) RETURNING id", UUID.class,
                "02:00:00:" + UUID.randomUUID().toString().substring(0, 8).replace('-', ':'));
    }

    private UUID create(UUID endpoint, String operator) {
        return store.create(endpoint, "02:00:00:00:00:01", "RESTRICT", operator,
                "Quarantine", null, null);
    }

    @Test
    void successAndDefinitiveFailureAreDurable() {
        UUID endpoint = endpoint();
        UUID success = create(endpoint, "alice");
        assertTrue(store.claim(success));
        store.finish(success, new IseResult(true, "ANC_APPLIED"));
        assertEquals("SUCCEEDED", state(success));
        assertFalse(store.claim(success));

        UUID rejected = store.create(endpoint, "02:00:00:00:00:02", "CLEAR_RESTRICTION", "bob", null, null, null);
        assertTrue(store.claim(rejected));
        store.finish(rejected, new IseResult(false, "401 Unauthorized"));
        assertEquals("FAILED", state(rejected));
    }

    @Test
    void databaseUniqueKeyCoalescesConcurrentDuplicateAndClaimsOnlyOnce() throws Exception {
        UUID endpoint = endpoint();
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); return create(endpoint, "same-operator"); });
            var second = pool.submit(() -> { start.await(); return create(endpoint, "same-operator"); });
            start.countDown();
            UUID one = first.get();
            UUID two = second.get();
            assertEquals(one, two);
            var a = pool.submit(() -> store.claim(one));
            var b = pool.submit(() -> store.claim(two));
            assertNotEquals(a.get(), b.get());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void retryBackoffIsBoundedAndAmbiguousCrashRequiresOperatorResolution() {
        UUID endpoint = endpoint();
        UUID retry = store.create(endpoint, "02:00:00:00:00:03", "RESTRICT", "retry", null, null, null);
        assertTrue(store.claim(retry));
        store.finish(retry, new IseResult(false, "Connection refused", false, true));
        assertEquals("PENDING", state(retry));
        assertTrue(jdbc.queryForObject("SELECT next_attempt_at > now() FROM ise_action_operation WHERE id=?", Boolean.class, retry));
        jdbc.update("UPDATE ise_action_operation SET next_attempt_at=now() WHERE id=?", retry);
        assertEquals(1, store.claimDueRetries(20).size());
        store.finish(retry, new IseResult(false, "Connection refused", false, true));
        assertEquals("FAILED", state(retry));

        UUID uncertain = store.create(endpoint, "02:00:00:00:00:04", "RESTRICT", "operator", null, null, null);
        assertTrue(store.claim(uncertain));
        store.finish(uncertain, new IseResult(false, "read timeout", true));
        assertEquals("RECONCILIATION_REQUIRED", state(uncertain));
        assertFalse(store.resolveAutomatically(uncertain, new IseReconciliationResult(
                IseReconciliationResult.Outcome.INCONCLUSIVE, "readback unavailable")));
        assertEquals("RECONCILIATION_REQUIRED", state(uncertain));
        assertTrue(store.resolveAutomatically(uncertain, new IseReconciliationResult(
                IseReconciliationResult.Outcome.SUCCEEDED, "ANC quarantine is present")));
        assertEquals("SUCCEEDED", state(uncertain));
        assertEquals("ISE_READBACK", jdbc.queryForObject(
                "SELECT resolved_by FROM ise_action_operation WHERE id=?", String.class, uncertain));
        assertFalse(store.resolve(uncertain, "admin", "FAILED", "duplicate resolution"));

        UUID uncertainFailure = store.create(endpoint, "02:00:00:00:00:05", "CLEAR_RESTRICTION", "operator", null, null, null);
        assertTrue(store.claim(uncertainFailure));
        store.finish(uncertainFailure, new IseResult(false, "read timeout", true));
        assertTrue(store.resolve(uncertainFailure, "admin", "FAILED", "ISE console confirms restriction is still active"));
        assertEquals("FAILED", state(uncertainFailure));
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM ise_action_audit WHERE action_type='OPERATION_RESOLVED'", Integer.class));
    }

    @Test
    void staleInProgressIsRecoveredWithoutReplay() {
        UUID id = create(endpoint(), "crashed");
        assertTrue(store.claim(id));
        jdbc.update("UPDATE ise_action_operation SET updated_at=now()-interval '10 minutes' WHERE id=?", id);
        assertEquals(1, store.recoverInterrupted());
        assertEquals("RECONCILIATION_REQUIRED", state(id));
        assertFalse(store.claim(id));
    }

    @Test
    void crashAfterExternalSuccessLeavesDurableOperationForReconciliation() {
        UUID endpointId = endpoint();
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        IseOperationStore crashBetweenCallAndRecord = new IseOperationStore(
                new JdbcTemplate(dataSource), new DataSourceTransactionManager(dataSource), 2, 300000, 30, 900) {
            @Override public void finish(UUID id, IseResult result) {
                throw new IllegalStateException("simulated process crash before local completion commit");
            }
        };
        EndpointRepository endpoints = mock(EndpointRepository.class);
        IseTransport transport = mock(IseTransport.class);
        when(endpoints.findById(endpointId)).thenReturn(Optional.of(Endpoint.builder()
                .id(endpointId).macAddress("02:00:00:00:00:09").build()));
        when(transport.publishEnforcement("02:00:00:00:00:09", EnforcementAction.RESTRICT, null))
                .thenReturn(new IseResult(true, "ANC_APPLIED"));
        IseActionService service = new IseActionService(endpoints, mock(AssessmentService.class), transport,
                mock(IseActionAuditRepository.class), crashBetweenCallAndRecord);

        assertThrows(IllegalStateException.class, () -> service.restrict(endpointId, null, "operator"));
        verify(transport).publishEnforcement("02:00:00:00:00:09", EnforcementAction.RESTRICT, null);
        UUID operationId = jdbc.queryForObject("SELECT id FROM ise_action_operation WHERE endpoint_id=? ORDER BY created_at DESC LIMIT 1",
                UUID.class, endpointId);
        assertEquals("IN_PROGRESS", state(operationId));
        jdbc.update("UPDATE ise_action_operation SET updated_at=now()-interval '10 minutes' WHERE id=?", operationId);
        store.recoverInterrupted();
        assertEquals("RECONCILIATION_REQUIRED", state(operationId));
        assertTrue(store.resolve(operationId, "admin", "SUCCEEDED", "ISE console confirms ANC quarantine"));
    }

    private String state(UUID id) {
        return jdbc.queryForObject("SELECT state FROM ise_action_operation WHERE id=?", String.class, id);
    }
}
