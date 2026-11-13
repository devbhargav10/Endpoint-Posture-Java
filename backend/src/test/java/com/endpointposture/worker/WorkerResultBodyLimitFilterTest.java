package com.endpointposture.worker;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkerResultBodyLimitFilterTest {
    private final WorkerResultBodyLimitFilter filter = new WorkerResultBodyLimitFilter();

    @Test
    void rejectsOversizedDeclaredBodyBeforeContinuing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/workers/jobs/123/result");
        request.setContent(new byte[WorkerResultBodyLimitFilter.MAX_REQUEST_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsOversizedChunkedBodyBeforeContinuing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/workers/jobs/123/result") {
            @Override public int getContentLength() { return -1; }
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setContent(new byte[WorkerResultBodyLimitFilter.MAX_REQUEST_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        verifyNoInteractions(chain);
    }
}
