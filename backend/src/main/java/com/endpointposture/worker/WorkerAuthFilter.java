package com.endpointposture.worker;

import com.endpointposture.security.LoginRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Authenticates X-Worker-Key only on worker routes; enrollment uses a one-time token. */
@Component
public class WorkerAuthFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Worker-Key";
    /** Accepted alias for {@link #HEADER}; HEADER wins when both are sent. */
    public static final String ALT_HEADER = "X-Worker-Api-Key";
    public static final String ENROLL_PATH = "/api/v1/workers/enroll";
    private static final String PREFIX = "/api/v1/workers/";

    private final WorkerRegistryService registry;
    private final WorkerAuditService audit;
    private final LoginRateLimiter authenticationIpLimiter;
    private final LoginRateLimiter unauthenticatedIpLimiter;
    private final LoginRateLimiter workerLimiter;

    public WorkerAuthFilter(
            WorkerRegistryService registry,
            WorkerAuditService audit,
            @Value("${app.workers.ingress.authentication-ip-capacity:6000}") int authenticationIpCapacity,
            @Value("${app.workers.ingress.authentication-ip-refill-tokens:6000}") int authenticationIpRefill,
            @Value("${app.workers.ingress.authentication-ip-refill-duration-seconds:60}") long authenticationIpRefillSeconds,
            @Value("${app.workers.ingress.unauthenticated-ip-capacity:120}") int ipCapacity,
            @Value("${app.workers.ingress.unauthenticated-ip-refill-tokens:120}") int ipRefill,
            @Value("${app.workers.ingress.unauthenticated-ip-refill-duration-seconds:60}") long ipRefillSeconds,
            @Value("${app.workers.ingress.authenticated-worker-capacity:600}") int workerCapacity,
            @Value("${app.workers.ingress.authenticated-worker-refill-tokens:600}") int workerRefill,
            @Value("${app.workers.ingress.authenticated-worker-refill-duration-seconds:60}") long workerRefillSeconds) {
        this.registry = registry;
        this.audit = audit;
        this.authenticationIpLimiter = new LoginRateLimiter(
                true, authenticationIpCapacity, authenticationIpRefill, authenticationIpRefillSeconds);
        this.unauthenticatedIpLimiter = new LoginRateLimiter(true, ipCapacity, ipRefill, ipRefillSeconds);
        this.workerLimiter = new LoginRateLimiter(true, workerCapacity, workerRefill, workerRefillSeconds);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith(PREFIX) || ENROLL_PATH.equals(uri);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null) {
            key = request.getHeader(ALT_HEADER);
        }
        String ip = request.getRemoteAddr();
        if (key == null) {
            if (!unauthenticatedIpLimiter.tryAcquire(ip)) {
                tooMany(response);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        boolean wellFormedKey = WorkerCredentials.looksLikeKey(key);
        if (wellFormedKey) {
            if (!authenticationIpLimiter.tryAcquire(ip)) {
                tooMany(response);
                return;
            }
        } else if (!unauthenticatedIpLimiter.tryAcquire(ip)) {
            tooMany(response);
            return;
        }

        WorkerRegistryService.AuthResult result = wellFormedKey
                ? registry.authenticate(key)
                : new WorkerRegistryService.AuthResult(WorkerRegistryService.Status.UNKNOWN_KEY, null, null, null);
        if (result.status() == WorkerRegistryService.Status.OK) {
            if (!workerLimiter.tryAcquire(result.workerId().toString())) {
                tooMany(response);
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    result.principal(), null, List.of(new SimpleGrantedAuthority("ROLE_WORKER"))));
            chain.doFilter(request, response);
            return;
        }

        if (wellFormedKey && !unauthenticatedIpLimiter.tryAcquire(ip)) {
            tooMany(response);
            return;
        }
        boolean revoked = result.status() == WorkerRegistryService.Status.REVOKED;
        audit.record(
                revoked ? "AUTH_REVOKED" : "AUTH_FAILED",
                result.workerId(),
                result.name(),
                revoked ? "Revoked worker key presented" : "Unknown or malformed worker key",
                ip);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("text/plain");
        response.getWriter().write("Invalid worker key");
    }

    private static void tooMany(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("text/plain");
        response.getWriter().write("Too many requests");
    }
}