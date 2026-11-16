package com.endpointposture.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Data access for {@link IseActionAudit}. Reads only ever list rows - the trail
 * is append-only.
 */
public interface IseActionAuditRepository extends JpaRepository<IseActionAudit, UUID> {

    List<IseActionAudit> findAllByEndpointIdOrderByOccurredAtDescIdDesc(UUID endpointId,
            org.springframework.data.domain.Pageable page);

    List<IseActionAudit> findAllByOrderByOccurredAtDescIdDesc(org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT DISTINCT ON (endpoint_id) *
            FROM ise_action_audit
            WHERE action_type IN ('RESTRICT', 'CLEAR_RESTRICTION')
            ORDER BY endpoint_id, occurred_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<IseActionAudit> findLatestEnforcementActionsPerEndpoint(
            @org.springframework.data.repository.query.Param("limit") int limit,
            @org.springframework.data.repository.query.Param("offset") long offset);
}
