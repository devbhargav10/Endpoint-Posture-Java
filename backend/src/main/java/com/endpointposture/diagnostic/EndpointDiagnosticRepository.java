package com.endpointposture.diagnostic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/** Data access for {@link EndpointDiagnostic}. Append-only: no update or delete path. */
public interface EndpointDiagnosticRepository extends JpaRepository<EndpointDiagnostic, UUID> {

    List<EndpointDiagnostic> findByEndpointIdOrderByCollectedAtDescIdDesc(UUID endpointId);

    List<EndpointDiagnostic> findByEndpointIdOrderByCollectedAtDescIdDesc(UUID endpointId, Pageable pageable);

    long countByEndpointId(UUID endpointId);

    /** @return its newest run, or empty if never diagnosed */
    Optional<EndpointDiagnostic> findFirstByEndpointIdOrderByCollectedAtDesc(UUID endpointId);
}
