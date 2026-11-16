package com.endpointposture.session;

import com.endpointposture.endpoint.EndpointNotFoundException;
import com.endpointposture.endpoint.EndpointRepository;
import com.endpointposture.api.ApiPaging;
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
    public List<SessionEventResponse> history(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        if (!endpoints.existsById(id)) throw new EndpointNotFoundException(id.toString());
        return logs.findByEndpointIdOrderByEventAtDescIdDesc(id, ApiPaging.of(page, size)).stream()
                .map(e -> new SessionEventResponse(e.getId(), e.getEventType(), e.getIpAddress(), e.getEventAt()))
                .toList();
    }
}
