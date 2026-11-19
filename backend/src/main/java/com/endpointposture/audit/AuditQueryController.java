package com.endpointposture.audit;

import com.endpointposture.api.ApiPaging;
import com.endpointposture.endpoint.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only view of every ISE action ever taken from this platform -
 * success or failure, always logged by
 * {@link com.endpointposture.ise.IseActionService}.
 */
@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "ISE audit trail", description = "Every Share Posture / Restrict / Clear Restriction action ever taken. Requires a bearer token.")
public class AuditQueryController {

    public record Summary(long total, long sharePosture, long restrict, long succeeded) {}

    private final IseActionAuditRepository repository;

    public AuditQueryController(IseActionAuditRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "Page through ISE actions, newest first",
            description = "Zero-based page; size defaults to 100 and is capped at 500. Filters are applied before paging.")
    @GetMapping("/ise-actions/page")
    public PageResponse<IseActionAudit> page(@RequestParam(required = false) UUID endpointId,
                                             @RequestParam(required = false) String actionType,
                                             @RequestParam(required = false) Boolean succeeded,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "100") int size) {
        var pageable = ApiPaging.of(page, size);
        String search = q == null || q.isBlank() ? null : q.trim();
        List<IseActionAudit> items = repository.findPageFiltered(endpointId, actionType, succeeded, search,
                pageable.getPageSize(), pageable.getOffset());
        long total = repository.countFiltered(endpointId, actionType, succeeded, search);
        return new PageResponse<>(items, total, pageable.getPageNumber(), pageable.getPageSize());
    }

    @Operation(summary = "ISE action counts")
    @GetMapping("/ise-actions/summary")
    public Summary summary() {
        long total = repository.count();
        return new Summary(total, repository.countSharePosture(), repository.countByActionType("RESTRICT"),
                repository.countBySucceededTrue());
    }

    /**
     * @param endpointId optional filter to one endpoint's history
     * @return matching audit rows, newest first
     */
    @Operation(summary = "List ISE actions, newest first, optionally filtered to one endpoint")
    @GetMapping("/ise-actions")
    public List<IseActionAudit> list(@RequestParam(required = false) UUID endpointId,
            @RequestParam(defaultValue = "500") int limit) {
        var page = org.springframework.data.domain.PageRequest.of(0, Math.max(1, Math.min(limit, 2000)));
        return endpointId != null
                ? repository.findAllByEndpointIdOrderByOccurredAtDescIdDesc(endpointId, page)
                : repository.findAllByOrderByOccurredAtDescIdDesc(page);
    }
}
