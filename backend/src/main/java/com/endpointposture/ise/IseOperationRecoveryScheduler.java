package com.endpointposture.ise;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

/** Marks abandoned calls for manual/explicit reconciliation; never replays enforcement blindly. */
@Component
public class IseOperationRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(IseOperationRecoveryScheduler.class);
    private final IseOperationStore operations;
    private final IseTransport transport;
    private final int retryBatchSize;

    public IseOperationRecoveryScheduler(IseOperationStore operations, IseTransport transport,
                                         @Value("${app.ise.operations.retry-batch-size:25}") int retryBatchSize) {
        this.operations = operations;
        this.transport = transport;
        this.retryBatchSize = Math.max(1, retryBatchSize);
    }

    @PostConstruct
    public void recoverOnStartup() { recover(); }

    @Scheduled(fixedDelayString = "${app.ise.operations.recovery-interval-ms:60000}")
    public void recover() {
        try { operations.recoverInterrupted(); }
        catch (Exception e) { log.error("ISE operation recovery failed; it will retry on the next sweep", e); }
        retryDue();
        reconcilePending();
    }

    private void retryDue() {
        try {
            for (IseOperation op : operations.claimDueRetries(retryBatchSize)) {
                IseResult result;
                try {
                    result = "SHARE_POSTURE".equals(op.actionType())
                            ? transport.publishPosture(op.macAddress(), op.postureStatus(), op.postureDetails())
                            : transport.publishEnforcement(op.macAddress(), "RESTRICT".equals(op.actionType())
                                    ? EnforcementAction.RESTRICT : EnforcementAction.CLEAR, op.policy());
                } catch (RuntimeException e) {
                    result = new IseResult(false, e.getMessage(), true);
                }
                operations.finish(op.id(), result);
            }
        } catch (Exception e) { log.error("ISE retry sweep failed; it will retry on the next interval", e); }
    }

    private void reconcilePending() {
        try {
            for (IseOperation operation : operations.reconciliationCandidates(retryBatchSize)) {
                IseReconciliationResult result = transport.reconcile(operation);
                if (result.outcome() == IseReconciliationResult.Outcome.INCONCLUSIVE) continue;
                operations.resolveAutomatically(operation.id(), result);
            }
        } catch (Exception e) {
            log.error("ISE readback reconciliation failed; unresolved operations remain available to administrators", e);
        }
    }
}
