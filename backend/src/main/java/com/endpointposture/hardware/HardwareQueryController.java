package com.endpointposture.hardware;

import com.endpointposture.hardware.dto.HardwareHealthResponse;
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

/** Read side: an endpoint's hardware-health history. */
@RestController
@RequestMapping("/api/v1/endpoints/{id}/hardware-health")
@Tag(name = "Hardware health history", description = "Read an endpoint's hardware-health reports. Requires a bearer token.")
public class HardwareQueryController {

    private final HardwareHealthService service;

    public HardwareQueryController(HardwareHealthService service) {
        this.service = service;
    }

    @Operation(summary = "Hardware health history for an endpoint, newest first")
    @GetMapping
    public List<HardwareHealthResponse> history(@PathVariable UUID id) {
        return service.getHistoryForEndpoint(id);
    }

    @Operation(summary = "Page through this endpoint history, newest first",
            description = "Zero-based page; size defaults to 100 and is capped at 500.")
    @GetMapping("/page")
    public PageResponse<HardwareHealthResponse> historyPage(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        var pageable = ApiPaging.of(page, size);
        return new PageResponse<>(service.getHistoryForEndpoint(id, pageable.getPageNumber(), pageable.getPageSize()), service.historyCount(id),
                pageable.getPageNumber(), pageable.getPageSize());
    }

    @Operation(summary = "Latest hardware health report for an endpoint")
    @GetMapping("/latest")
    public ResponseEntity<HardwareHealthResponse> latest(@PathVariable UUID id) {
     try {
        return ResponseEntity.ok(service.getLatestForEndpoint(id));
        } catch (HardwareHealthNotFoundException e) {
        return ResponseEntity.noContent().build();
    }
}
}
