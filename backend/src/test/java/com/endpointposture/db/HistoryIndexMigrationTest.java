package com.endpointposture.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class HistoryIndexMigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("migration_upgrade_test").withUsername("test").withPassword("test");

    @Test
    void v32ToV33PreservesExistingAssessmentAndCreatesHistoryIndexes() throws Exception {
        Flyway v32 = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("32")).load();
        v32.migrate();

        UUID endpointId;
        UUID assessmentId;
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (PreparedStatement endpoint = connection.prepareStatement(
                    "INSERT INTO endpoint (mac_address) VALUES (?) RETURNING id")) {
                endpoint.setString(1, "F0:00:00:00:00:01");
                try (ResultSet result = endpoint.executeQuery()) {
                    result.next();
                    endpointId = result.getObject(1, UUID.class);
                }
            }
            try (PreparedStatement assessment = connection.prepareStatement(
                    "INSERT INTO assessment (endpoint_id, status, started_at, completed_at, detail) " +
                            "VALUES (?, 'COMPLIANT', now(), now(), 'before V33') RETURNING id")) {
                assessment.setObject(1, endpointId);
                try (ResultSet result = assessment.executeQuery()) {
                    result.next();
                    assessmentId = result.getObject(1, UUID.class);
                }
            }
        }

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM assessment WHERE id = ? AND endpoint_id = ? AND detail = 'before V33'",
                Integer.class, assessmentId, endpointId));
        assertTrue(jdbc.queryForObject("SELECT to_regclass('idx_assessment_endpoint_created_id') IS NOT NULL", Boolean.class));
        assertTrue(jdbc.queryForObject("SELECT to_regclass('idx_ise_audit_occurred_id') IS NOT NULL", Boolean.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = current_schema() AND indexname = 'idx_assessment_endpoint_created'",
                Integer.class));
    }
}
