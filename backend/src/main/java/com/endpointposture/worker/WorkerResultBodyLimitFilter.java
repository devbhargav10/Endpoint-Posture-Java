package com.endpointposture.worker;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Caps worker result bodies before Jackson parses them, including chunked requests. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkerResultBodyLimitFilter extends OncePerRequestFilter {
    public static final int MAX_REQUEST_BYTES = 1_200_000;
    private static final String WORKER_PATH = "/api/v1/workers/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().startsWith(WORKER_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
            reject(response);
            return;
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream(Math.max(0, request.getContentLength()));
        byte[] buffer = new byte[8192];
        int read;
        while ((read = request.getInputStream().read(buffer)) != -1) {
            if (body.size() + read > MAX_REQUEST_BYTES) {
                reject(response);
                return;
            }
            body.write(buffer, 0, read);
        }
        byte[] bytes = body.toByteArray();
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public int getContentLength() { return bytes.length; }
            @Override public long getContentLengthLong() { return bytes.length; }
            @Override public ServletInputStream getInputStream() {
                ByteArrayInputStream input = new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException("Async body reads are not supported");
                    }
                };
            }
        }, response);
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setContentType("text/plain");
        response.getWriter().write("Worker result request is too large");
    }
}
