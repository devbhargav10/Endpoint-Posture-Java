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

    long countByEndpointId(UUID endpointId);
    long countByActionType(String actionType);
    long countBySucceededTrue();

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT * FROM ise_action_audit
             WHERE (CAST(:endpointId AS uuid) IS NULL OR endpoint_id = :endpointId)
               AND (CAST(:actionType AS text) IS NULL OR action_type = :actionType)
               AND (CAST(:succeeded AS boolean) IS NULL OR succeeded = :succeeded)
               AND (CAST(:q AS text) IS NULL
                    OR action_type ILIKE '%' || :q || '%'
                    OR coalesce(operator, '') ILIKE '%' || :q || '%'
                    OR coalesce(detail, '') ILIKE '%' || :q || '%'
                    OR endpoint_id::text ILIKE '%' || :q || '%')
             ORDER BY occurred_at DESC, id DESC
             LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<IseActionAudit> findPageFiltered(
            @org.springframework.data.repository.query.Param("endpointId") UUID endpointId,
            @org.springframework.data.repository.query.Param("actionType") String actionType,
            @org.springframework.data.repository.query.Param("succeeded") Boolean succeeded,
            @org.springframework.data.repository.query.Param("q") String q,
            @org.springframework.data.repository.query.Param("limit") int limit,
            @org.springframework.data.repository.query.Param("offset") long offset);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT count(*) FROM ise_action_audit
             WHERE (CAST(:endpointId AS uuid) IS NULL OR endpoint_id = :endpointId)
               AND (CAST(:actionType AS text) IS NULL OR action_type = :actionType)
               AND (CAST(:succeeded AS boolean) IS NULL OR succeeded = :succeeded)
               AND (CAST(:q AS text) IS NULL
                    OR action_type ILIKE '%' || :q || '%'
                    OR coalesce(operator, '') ILIKE '%' || :q || '%'
                    OR coalesce(detail, '') ILIKE '%' || :q || '%'
                    OR endpoint_id::text ILIKE '%' || :q || '%')
            """, nativeQuery = true)
    long countFiltered(
            @org.springframework.data.repository.query.Param("endpointId") UUID endpointId,
            @org.springframework.data.repository.query.Param("actionType") String actionType,
            @org.springframework.data.repository.query.Param("succeeded") Boolean succeeded,
            @org.springframework.data.repository.query.Param("q") String q);

    @org.springframework.data.jpa.repository.Query("select count(a) from IseActionAudit a where a.actionType = 'SHARE_POSTURE'")
    long countSharePosture();

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT count(DISTINCT endpoint_id) FROM ise_action_audit
             WHERE action_type IN ('RESTRICT', 'CLEAR_RESTRICTION')
            """, nativeQuery = true)
    long countLatestEnforcementEndpoints();

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
