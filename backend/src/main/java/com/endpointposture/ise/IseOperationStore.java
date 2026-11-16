package com.endpointposture.ise;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/** PostgreSQL-backed operation ledger. Each transition commits independently of ISE. */
@Repository
public class IseOperationStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final int maxAttempts;
    private final int staleAfterMs;
    private final int retryBaseSeconds;
    private final int retryMaxSeconds;

    public IseOperationStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            @Value("${app.ise.operations.max-attempts:3}") int maxAttempts,
            @Value("${app.ise.operations.stale-after-ms:300000}") int staleAfterMs,
            @Value("${app.ise.operations.retry-base-seconds:30}") int retryBaseSeconds,
            @Value("${app.ise.operations.retry-max-seconds:900}") int retryMaxSeconds) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.staleAfterMs = Math.max(1000, staleAfterMs);
        this.retryBaseSeconds = Math.max(1, retryBaseSeconds);
        this.retryMaxSeconds = Math.max(this.retryBaseSeconds, retryMaxSeconds);
    }

    public UUID create(UUID endpointId, String mac, String action, String operator, String policy,
                       String postureStatus, String postureDetails) {
        String key = key(endpointId, action, operator, policy, postureStatus, postureDetails);
        return transactions.execute(status -> jdbc.queryForObject("""
                INSERT INTO ise_action_operation(
                    endpoint_id, mac_address, action_type, operation_key, operator, policy,
                    posture_status, posture_details, state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                ON CONFLICT (operation_key) WHERE state IN ('PENDING', 'IN_PROGRESS')
                DO UPDATE SET operation_key = EXCLUDED.operation_key
                RETURNING id
                """, UUID.class, endpointId, mac, action, key, operator, policy, postureStatus, postureDetails));
    }

    public boolean claim(UUID id) {
        return Boolean.TRUE.equals(transactions.execute(status -> jdbc.update("""
                UPDATE ise_action_operation SET state='IN_PROGRESS', attempt_count=attempt_count+1, updated_at=now()
                WHERE id=? AND state='PENDING' AND next_attempt_at <= now()
                """, id) == 1));
    }

    public void finish(UUID id, IseResult result) {
        transactions.executeWithoutResult(status -> {
            java.util.List<IseOperation> completed = jdbc.query("""
                UPDATE ise_action_operation
                SET state = CASE WHEN ? THEN 'PENDING' ELSE ? END,
                    result_detail = ?,
                    updated_at = now(),
                    completed_at = CASE WHEN ? THEN NULL ELSE now() END,
                    next_attempt_at = CASE WHEN ? THEN now() + make_interval(
                        secs => LEAST(?, (? * power(2, GREATEST(0, attempt_count - 1)))::int))
                        ELSE next_attempt_at END
                WHERE id = ? AND state = 'IN_PROGRESS'
                RETURNING endpoint_id, mac_address, action_type, operator, policy, posture_status, posture_details
                """, (rs, row) -> new IseOperation(
                        null, rs.getObject("endpoint_id", UUID.class), rs.getString("mac_address"),
                        rs.getString("action_type"), rs.getString("operator"), rs.getString("policy"),
                        rs.getString("posture_status"), rs.getString("posture_details")),
                result.retryable() && !result.ambiguous() && jdbc.queryForObject(
                        "SELECT attempt_count < ? FROM ise_action_operation WHERE id=?", Boolean.class, maxAttempts, id),
                result.ambiguous() ? "RECONCILIATION_REQUIRED" : result.success() ? "SUCCEEDED" : "FAILED",
                result.detail(), result.retryable() && !result.ambiguous(),
                result.retryable() && !result.ambiguous(), retryMaxSeconds, retryBaseSeconds, id)
                    .stream().toList();
            if (completed.isEmpty()) return;
            IseOperation operation = completed.get(0);
            jdbc.update("""
                    INSERT INTO ise_action_audit(endpoint_id, action_type, operator, succeeded, detail, occurred_at)
                    VALUES (?, ?, ?, ?, ?, now())
                    """, operation.endpointId(), operation.actionType(), operation.operator(),
                    result.success(), result.detail());
        });
    }

    /** A process may have died after ISE acted; never replay an in-flight action blindly. */
    public int recoverInterrupted() {
        return transactions.execute(status -> jdbc.update("""
                UPDATE ise_action_operation SET state='RECONCILIATION_REQUIRED', result_detail='Backend restarted while operation was in progress', updated_at=now()
                WHERE state = 'IN_PROGRESS' AND updated_at < now() - (? * interval '1 millisecond')
                """, staleAfterMs));
    }

    public boolean resolve(UUID id, String actor, String resolution, String detail) {
        String state = "SUCCEEDED".equals(resolution) ? "SUCCEEDED" : "FAILED";
        return Boolean.TRUE.equals(transactions.execute(tx -> {
            IseOperation op = jdbc.query("""
                    SELECT id, endpoint_id, action_type, operator FROM ise_action_operation
                    WHERE id = ? AND state = 'RECONCILIATION_REQUIRED' FOR UPDATE
                    """, (rs, row) -> new IseOperation(rs.getObject("id", UUID.class),
                    rs.getObject("endpoint_id", UUID.class), null, rs.getString("action_type"),
                    rs.getString("operator"), null, null, null), id)
                    .stream().findFirst().orElse(null);
            if (op == null) return false;
            int updated = jdbc.update("""
                    UPDATE ise_action_operation
                    SET state = ?, resolution = ?, resolution_detail = ?, resolved_by = ?, resolved_at = now(),
                        result_detail = ?, updated_at = now(), completed_at = now()
                    WHERE id = ? AND state = 'RECONCILIATION_REQUIRED'
                    """, state, resolution, detail, actor, detail, id);
            if (updated != 1) return false;
            insertResolutionAudit(op, actor, "SUCCEEDED".equals(resolution), id, detail);
            return true;
        }));
    }

    public java.util.List<java.util.Map<String, Object>> pendingReconciliation(int limit) {
        return jdbc.queryForList("""
                SELECT id,endpoint_id,mac_address,action_type,operator,policy,result_detail,updated_at
                FROM ise_action_operation WHERE state='RECONCILIATION_REQUIRED' ORDER BY updated_at LIMIT ?
                """, limit);
    }

    public java.util.List<IseOperation> reconciliationCandidates(int limit) {
        return jdbc.query("""
                SELECT id, endpoint_id, mac_address, action_type, operator, policy, posture_status, posture_details
                FROM ise_action_operation WHERE state = 'RECONCILIATION_REQUIRED'
                ORDER BY updated_at LIMIT ?
                """, (rs, row) -> new IseOperation(rs.getObject("id", UUID.class),
                rs.getObject("endpoint_id", UUID.class), rs.getString("mac_address"),
                rs.getString("action_type"), rs.getString("operator"), rs.getString("policy"),
                rs.getString("posture_status"), rs.getString("posture_details")), limit);
    }

    public boolean resolveAutomatically(UUID id, IseReconciliationResult result) {
        if (result.outcome() == IseReconciliationResult.Outcome.INCONCLUSIVE) return false;
        String state = result.outcome().name();
        return Boolean.TRUE.equals(transactions.execute(tx -> {
            IseOperation op = jdbc.query("""
                    SELECT id, endpoint_id, action_type, operator FROM ise_action_operation
                    WHERE id = ? AND state = 'RECONCILIATION_REQUIRED' FOR UPDATE
                    """, (rs, row) -> new IseOperation(rs.getObject("id", UUID.class),
                    rs.getObject("endpoint_id", UUID.class), null, rs.getString("action_type"),
                    rs.getString("operator"), null, null, null), id).stream().findFirst().orElse(null);
            if (op == null) return false;
            int updated = jdbc.update("""
                    UPDATE ise_action_operation
                    SET state = ?, resolution = ?, resolution_detail = ?, resolved_by = 'ISE_READBACK',
                        resolved_at = now(), result_detail = ?, updated_at = now(), completed_at = now()
                    WHERE id = ? AND state = 'RECONCILIATION_REQUIRED'
                    """, state, state, result.detail(), result.detail(), id);
            if (updated != 1) return false;
            insertResolutionAudit(op, "ISE_READBACK", "SUCCEEDED".equals(state), id, result.detail());
            return true;
        }));
    }

    public java.util.List<IseOperation> claimDueRetries(int limit) {
        return transactions.execute(tx -> jdbc.query("""
                WITH picked AS (
                    SELECT id FROM ise_action_operation WHERE state = 'PENDING' AND next_attempt_at <= now()
                    ORDER BY next_attempt_at, created_at LIMIT ? FOR UPDATE SKIP LOCKED
                )
                UPDATE ise_action_operation o SET state = 'IN_PROGRESS', attempt_count = attempt_count + 1, updated_at = now()
                FROM picked WHERE o.id=picked.id
                RETURNING o.id,o.endpoint_id,o.mac_address,o.action_type,o.operator,o.policy,o.posture_status,o.posture_details
                """, (rs, row) -> new IseOperation(rs.getObject("id", UUID.class),
                rs.getObject("endpoint_id", UUID.class), rs.getString("mac_address"), rs.getString("action_type"),
                rs.getString("operator"), rs.getString("policy"), rs.getString("posture_status"),
                rs.getString("posture_details")), limit));
    }

    private String key(UUID endpointId, String action, String operator, String policy, String status, String details) {
        try {
            StringBuilder value = new StringBuilder();
            appendKeyPart(value, endpointId.toString());
            appendKeyPart(value, action);
            appendKeyPart(value, operator);
            appendKeyPart(value, policy);
            appendKeyPart(value, status);
            appendKeyPart(value, details);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create ISE operation idempotency key", e);
        }
    }

    private void appendKeyPart(StringBuilder key, String value) {
        if (value == null) key.append("-1:");
        else key.append(value.length()).append(':').append(value);
    }

    private void insertResolutionAudit(IseOperation operation, String actor, boolean succeeded,
                                       UUID operationId, String detail) {
        jdbc.update("""
                INSERT INTO ise_action_audit(endpoint_id, action_type, operator, succeeded, detail, occurred_at)
                VALUES (?, 'OPERATION_RESOLVED', ?, ?, ?, now())
                """, operation.endpointId(), actor, succeeded,
                "Resolved operation " + operationId + " (" + operation.actionType() + "): " + detail);
    }
}
