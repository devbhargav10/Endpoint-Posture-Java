package com.endpointposture.ise;

public record IseReconciliationResult(Outcome outcome, String detail) {
    public enum Outcome { SUCCEEDED, FAILED, INCONCLUSIVE }
}
