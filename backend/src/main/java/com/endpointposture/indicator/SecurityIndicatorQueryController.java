package com.endpointposture.indicator;

import com.endpointposture.indicator.dto.SecurityIndicatorResponse;
import com.endpointposture.api.ApiPaging;
import com.endpointposture.endpoint.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Read side: retrieves security-indicator history. */
@RestController
@RequestMapping("/api/v1/endpoints/{id}/security-indicators")
@Tag(name = "Security indicators history",
     description = "Requires a bearer token.")
public class SecurityIndicatorQueryController {

    private final SecurityIndicatorService service;

    public SecurityIndicatorQueryController(SecurityIndicatorService service) {
        this.service = service;
    }

    @Operation(summary = "Security-indicator history for an endpoint, newest first")
    @GetMapping
    public List<SecurityIndicatorResponse> history(@PathVariable UUID id) {
        return service.history(id);
    }

    @Operation(summary = "Page through this endpoint history, newest first",
            description = "Zero-based page; size defaults to 100 and is capped at 500.")
    @GetMapping("/page")
    public PageResponse<SecurityIndicatorResponse> historyPage(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var pageable = ApiPaging.of(page, size);
        return new PageResponse<>(service.history(id, pageable.getPageNumber(), pageable.getPageSize()), service.historyCount(id),
                pageable.getPageNumber(), pageable.getPageSize());
    }

    @Operation(summary = "Latest run", description = "204 when never scanned.")
    @GetMapping("/latest")
    public ResponseEntity<SecurityIndicatorResponse> latest(
            @PathVariable UUID id) {
        return service.latest(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
