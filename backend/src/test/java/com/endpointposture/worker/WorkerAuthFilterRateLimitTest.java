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
        WorkerAuthFilter filter = filter(mock(WorkerRegistryService.class), 100, 100, 1, 0, 100);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request(null), new MockHttpServletResponse(), chain);
        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(request(null), limited, chain);

        assertEquals(429, limited.getStatus());
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void credentialHashLookupsAreCappedBeforeDatabaseAuthentication() throws Exception {
        WorkerRegistryService registry = mock(WorkerRegistryService.class);
        String unknownKey = WorkerCredentials.KEY_PREFIX + "workerkey000000000000000000000001";
        WorkerAuthFilter filter = filter(registry, 1, 0, 100, 100, 100);
        when(registry.authenticate(unknownKey)).thenReturn(new WorkerRegistryService.AuthResult(
                WorkerRegistryService.Status.UNKNOWN_KEY, null, null, null));
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request(unknownKey), new MockHttpServletResponse(), chain);
        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(request(unknownKey), limited, chain);

        assertEquals(429, limited.getStatus());
        verify(registry, times(1)).authenticate(unknownKey);
    }

    @Test
    void authenticatedWorkersBehindSameIpHaveIndependentBudgets() throws Exception {
        WorkerRegistryService registry = mock(WorkerRegistryService.class);
        when(registry.authenticate(anyString())).thenAnswer(call -> {
            String key = call.getArgument(0);
            UUID id = UUID.nameUUIDFromBytes(key.getBytes());
            return new WorkerRegistryService.AuthResult(WorkerRegistryService.Status.OK, id, key, "shard-a");
        });
        WorkerAuthFilter filter = filter(registry, 100, 100, 10, 0, 1);
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

    private static WorkerAuthFilter filter(
            WorkerRegistryService registry,
            int authenticationCapacity,
            int authenticationRefill,
            int unauthenticatedCapacity,
            int unauthenticatedRefill,
            int workerCapacity) {
        return new WorkerAuthFilter(
                registry,
                mock(WorkerAuditService.class),
                authenticationCapacity,
                authenticationRefill,
                60,
                unauthenticatedCapacity,
                unauthenticatedRefill,
                60,
                workerCapacity,
                100,
                60);
    }

    private static MockHttpServletRequest request(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);
        request.setRequestURI(PATH);
        request.setRemoteAddr("192.0.2.10");
        if (key != null) {
            request.addHeader(WorkerAuthFilter.HEADER, key);
        }
        return request;
    }
}