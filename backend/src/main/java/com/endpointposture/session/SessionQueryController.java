package com.endpointposture.session;

import com.endpointposture.endpoint.EndpointNotFoundException;
import com.endpointposture.endpoint.EndpointRepository;
import com.endpointposture.api.ApiPaging;
import com.endpointposture.endpoint.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestParam;

/** Read-only connect/disconnect history for one endpoint. Never calls ISE. */
@RestController
@RequestMapping("/api/v1/endpoints/{id}/sessions")
@Tag(name = "Session history", description = "Connect/disconnect events for an endpoint. Requires a bearer token.")
public class SessionQueryController {

    public record SessionEventResponse(UUID id, SessionEventType eventType, String ipAddress, Instant eventAt) {}

    private final EndpointSessionLogRepository logs;
    private final EndpointRepository endpoints;

    public SessionQueryController(EndpointSessionLogRepository logs, EndpointRepository endpoints) {
        this.logs = logs;
        this.endpoints = endpoints;
    }

    @Operation(summary = "Session history for an endpoint, newest first")
    @GetMapping
    public List<SessionEventResponse> history(@PathVariable UUID id) {
        requireEndpoint(id);
        return map(logs.findByEndpointIdOrderByEventAtDescIdDesc(id));
    }

    @Operation(summary = "Page through session history, newest first",
            description = "Zero-based page; size defaults to 100 and is capped at 500.")
    @GetMapping("/page")
    public PageResponse<SessionEventResponse> historyPage(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        requireEndpoint(id);
        var pageable = ApiPaging.of(page, size);
        return new PageResponse<>(map(logs.findByEndpointIdOrderByEventAtDescIdDesc(id, pageable)),
                logs.countByEndpointId(id), pageable.getPageNumber(), pageable.getPageSize());
    }

    private void requireEndpoint(UUID id) {
        if (!endpoints.existsById(id)) throw new EndpointNotFoundException(id.toString());
    }

    private static List<SessionEventResponse> map(List<EndpointSessionLog> events) {
        return events.stream().map(e -> new SessionEventResponse(e.getId(), e.getEventType(), e.getIpAddress(), e.getEventAt()))
                .toList();
    }
}
