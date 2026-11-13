package com.endpointposture.worker;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class WorkerAuthFilterRateLimitTest {
    private static final String PATH = "/api/v1/workers/jobs/claim";

    @Test
    void missingCredentialsStillConsumeUnauthenticatedIpBudget() throws Exception {
        WorkerAuthFilter filter = new WorkerAuthFilter(mock(WorkerRegistryService.class), mock(WorkerAuditService.class),
                1, 0, 60, 100, 100, 60);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request(null), new MockHttpServletResponse(), chain);
        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(request(null), limited, chain);

        assertEquals(429, limited.getStatus());
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void authenticatedWorkersBehindSameIpHaveIndependentBudgets() throws Exception {
        WorkerRegistryService registry = mock(WorkerRegistryService.class);
        when(registry.authenticate(anyString())).thenAnswer(call -> {
            String key = call.getArgument(0);
            UUID id = UUID.nameUUIDFromBytes(key.getBytes());
            return new WorkerRegistryService.AuthResult(WorkerRegistryService.Status.OK, id, key, "shard-a");
        });
        WorkerAuthFilter filter = new WorkerAuthFilter(registry, mock(WorkerAuditService.class),
                10, 0, 60, 1, 0, 60);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request(WorkerCredentials.KEY_PREFIX + "workerkey000000000000000000000001"), first, chain);
        filter.doFilter(request(WorkerCredentials.KEY_PREFIX + "workerkey000000000000000000000002"), second, chain);
        MockHttpServletResponse firstLimited = new MockHttpServletResponse();
        filter.doFilter(request(WorkerCredentials.KEY_PREFIX + "workerkey000000000000000000000001"), firstLimited, chain);

        assertEquals(200, first.getStatus());
        assertEquals(200, second.getStatus());
        assertEquals(429, firstLimited.getStatus());
        verify(chain, times(2)).doFilter(any(), any());
    }

    private static MockHttpServletRequest request(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);
        request.setRemoteAddr("192.0.2.10");
        if (key != null) request.addHeader(WorkerAuthFilter.HEADER, key);
        return request;
    }
}
