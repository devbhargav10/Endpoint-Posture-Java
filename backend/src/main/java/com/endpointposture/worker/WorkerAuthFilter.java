package com.endpointposture.worker;
import com.endpointposture.security.LoginRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    public static final String HEADER="X-Worker-Key";
    /** Accepted alias for {@link #HEADER}; HEADER wins when both are sent. */
    public static final String ALT_HEADER="X-Worker-Api-Key";
    public static final String ENROLL_PATH="/api/v1/workers/enroll";
    private static final String PREFIX="/api/v1/workers/";
    private final WorkerRegistryService registry;
    private final WorkerAuditService audit;
    private final LoginRateLimiter unauthenticatedIpLimiter;
    private final LoginRateLimiter workerLimiter;
    public WorkerAuthFilter(WorkerRegistryService registry, WorkerAuditService audit,
            @Value("${app.workers.ingress.unauthenticated-ip-capacity:120}") int ipCapacity,
            @Value("${app.workers.ingress.unauthenticated-ip-refill-tokens:120}") int ipRefill,
            @Value("${app.workers.ingress.unauthenticated-ip-refill-duration-seconds:60}") long ipRefillSeconds,
            @Value("${app.workers.ingress.authenticated-worker-capacity:600}") int workerCapacity,
            @Value("${app.workers.ingress.authenticated-worker-refill-tokens:600}") int workerRefill,
            @Value("${app.workers.ingress.authenticated-worker-refill-duration-seconds:60}") long workerRefillSeconds) {
        this.registry=registry; this.audit=audit;
        this.unauthenticatedIpLimiter=new LoginRateLimiter(true,ipCapacity,ipRefill,ipRefillSeconds);
        this.workerLimiter=new LoginRateLimiter(true,workerCapacity,workerRefill,workerRefillSeconds);
    }
    @Override protected boolean shouldNotFilter(@NonNull HttpServletRequest req){
        String uri=req.getRequestURI(); return !uri.startsWith(PREFIX)||ENROLL_PATH.equals(uri);
    }
    @Override protected void doFilterInternal(@NonNull HttpServletRequest req,@NonNull HttpServletResponse res,
                                               @NonNull FilterChain chain)throws ServletException,IOException{
        String key=req.getHeader(HEADER);
        if(key==null)key=req.getHeader(ALT_HEADER);
        String ip=req.getRemoteAddr();
        // Count missing and malformed credentials before continuing; valid worker traffic
        // is charged only to its worker bucket, so a shared NAT does not combine quotas.
        if(key==null){
            if(!unauthenticatedIpLimiter.tryAcquire(ip)){tooMany(res);return;}
            chain.doFilter(req,res);return;
        }
        if(!WorkerCredentials.looksLikeKey(key) && !unauthenticatedIpLimiter.tryAcquire(ip)){
            tooMany(res);return;
        }
        WorkerRegistryService.AuthResult result=WorkerCredentials.looksLikeKey(key)?registry.authenticate(key):
          new WorkerRegistryService.AuthResult(WorkerRegistryService.Status.UNKNOWN_KEY,null,null,null);
        if(result.status()==WorkerRegistryService.Status.OK){
            if(!workerLimiter.tryAcquire(result.workerId().toString())){tooMany(res);return;}
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
              result.principal(),null,List.of(new SimpleGrantedAuthority("ROLE_WORKER"))));
            chain.doFilter(req,res);return;
        }
        if(WorkerCredentials.looksLikeKey(key) && !unauthenticatedIpLimiter.tryAcquire(ip)){
            tooMany(res);return;
        }
        boolean revoked=result.status()==WorkerRegistryService.Status.REVOKED;
        audit.record(revoked?"AUTH_REVOKED":"AUTH_FAILED",result.workerId(),result.name(),
          revoked?"Revoked worker key presented":"Unknown or malformed worker key",ip);
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);res.setContentType("text/plain");res.getWriter().write("Invalid worker key");
    }
    private static void tooMany(HttpServletResponse res)throws IOException{
        res.setStatus(429);res.setContentType("text/plain");res.getWriter().write("Too many requests");
    }
}
