package com.endpointposture.diagnostic;

import com.endpointposture.diagnostic.dto.DiagnosticResponse;
import com.endpointposture.api.ApiPaging;
import com.endpointposture.endpoint.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestParam;

/** Read side: an endpoint's diagnostic history. Requires a bearer token. */
@RestController
@RequestMapping("/api/v1/endpoints/{id}/diagnostics")
@Tag(name = "Diagnostics history", description = "Read an endpoint's diagnostic runs. Requires a bearer token.")
public class DiagnosticQueryController {

    private final DiagnosticService service;

    public DiagnosticQueryController(DiagnosticService service) {
        this.service = service;
    }

    @Operation(summary = "Diagnostic history for an endpoint, newest first")
    @GetMapping
    public List<DiagnosticResponse> history(@PathVariable UUID id) {
        return service.history(id);
    }

    @Operation(summary = "Page through this endpoint history, newest first",
            description = "Zero-based page; size defaults to 100 and is capped at 500.")
    @GetMapping("/page")
    public PageResponse<DiagnosticResponse> historyPage(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var pageable = ApiPaging.of(page, size);
        return new PageResponse<>(service.history(id, pageable.getPageNumber(), pageable.getPageSize()), service.historyCount(id),
                pageable.getPageNumber(), pageable.getPageSize());
    }

    @Operation(summary = "Latest diagnostic run for an endpoint",
            description = "Returns 204 No Content when the endpoint has never been diagnosed.")
    @GetMapping("/latest")
    public ResponseEntity<DiagnosticResponse> latest(@PathVariable UUID id) {
        return service.latest(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
