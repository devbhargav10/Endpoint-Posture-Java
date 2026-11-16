// src/main/java/com/endpointposture/session/EndpointSessionLogRepository.java
package com.endpointposture.session;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface EndpointSessionLogRepository extends JpaRepository<EndpointSessionLog, UUID> {
    List<EndpointSessionLog> findByEndpointIdOrderByEventAtDescIdDesc(UUID endpointId, Pageable pageable);
}
