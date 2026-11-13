package com.endpointposture.audit;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One record of an operator triggering Share Posture, Restrict, Clear
 * Restriction, or resolving an ambiguous ISE operation.
 *
 * <p>Maps to the {@code ise_action_audit} table
 * ({@code V11__create_ise_action_audit.sql}). Written unconditionally -
 * success or failure - by the operation completion or resolution transaction,
 * so the audit trail remains atomic with durable operation state
 * (project plan Section 13).</p>
 */
@Entity
@Table(name = "ise_action_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IseActionAudit {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    /** {@code SHARE_POSTURE}, {@code RESTRICT}, or {@code CLEAR_RESTRICTION}. */
    @Column(name = "action_type", nullable = false)
    private String actionType;

    /** Username of the operator who triggered this action (the JWT subject). */
    private String operator;

    /** Whether ISE accepted/confirmed the action. */
    @Column(nullable = false)
    private boolean succeeded;

    /** Confirmation text on success, or the failure reason on failure. */
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) occurredAt = Instant.now();
    }
}
