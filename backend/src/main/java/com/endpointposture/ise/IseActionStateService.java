package com.endpointposture.ise;

import com.endpointposture.api.ApiPaging;
import com.endpointposture.audit.IseActionAuditRepository;
import com.endpointposture.ise.dto.IseActionStateResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IseActionStateService {

    private final IseActionAuditRepository auditRepository;

    public IseActionStateService(IseActionAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * @return the latest RESTRICT or CLEAR_RESTRICTION audit row per endpoint
     */
    @Transactional(readOnly = true)
    public List<IseActionStateResponse> getLatestEnforcementStates() {
        return getLatestEnforcementStates(0, ApiPaging.DEFAULT_SIZE);
    }

    @Transactional(readOnly = true)
    public List<IseActionStateResponse> getLatestEnforcementStates(int page, int size) {
        var pageable = ApiPaging.of(page, size);
        return auditRepository.findLatestEnforcementActionsPerEndpoint(pageable.getPageSize(), pageable.getOffset())
                .stream()
                .map(IseActionStateResponse::from)
                .toList();
    }
}
