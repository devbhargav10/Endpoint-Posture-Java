package com.endpointposture.indicator;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EndpointSecurityIndicatorRepository extends JpaRepository<EndpointSecurityIndicator, UUID> {
    List<EndpointSecurityIndicator> findByEndpointIdOrderByCollectedAtDescIdDesc(UUID endpointId);
    List<EndpointSecurityIndicator> findByEndpointIdOrderByCollectedAtDescIdDesc(UUID endpointId, Pageable pageable);
    long countByEndpointId(UUID endpointId);
    Optional<EndpointSecurityIndicator> findFirstByEndpointIdOrderByCollectedAtDesc(UUID endpointId);
}
