package ar.edu.uade.medlink.cloud;

import ar.edu.uade.medlink.common.error.ErrorResponses;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.LoggerFactory;

/** Cloud abuse protection; prescription comparison and business rules are unchanged. */
@Component
@Profile("cloud")
@Order(Ordered.LOWEST_PRECEDENCE)
public class CloudRequestGuard extends OncePerRequestFilter {
    private final ErrorResponses errors;
    private final Clock clock;
    private final CloudScanPolicy policy;
    private final CloudScanBudget budget;
    private final Semaphore scans = new Semaphore(1);
    private final Map<String,Integer> requests = new HashMap<>();
    private long window = Long.MIN_VALUE;
    private int total;
    public CloudRequestGuard(ErrorResponses errors,Clock clock,CloudScanPolicy policy,CloudScanBudget budget) {
        this.errors=errors; this.clock=clock; this.policy=policy; this.budget=budget;
    }
    private synchronized boolean allowAuth(String client) {
        long now=Math.floorDiv(clock.instant().getEpochSecond(),60);
        if (now!=window) { requests.clear(); total=0; window=now; }
        if (total>=60 || requests.size()>=4096 || requests.getOrDefault(client,0)>=10) return false;
        requests.merge(client,1,Integer::sum); total++; return true;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,
        FilterChain chain) throws IOException,ServletException {
        String path=request.getRequestURI();
        if ("POST".equals(request.getMethod()) && path.startsWith("/api/auth/")
            && !allowAuth(request.getRemoteAddr())) {
            response.setHeader("Retry-After","60");
            errors.write(request,response,429,"AUTH_LIMIT","Demasiados intentos. Esperá un minuto."); return;
        }
        if (!"POST".equals(request.getMethod()) || !path.equals("/api/me/recetas-demo/escaneo")) {
            chain.doFilter(request,response); return;
        }
        try {
            var auth=SecurityContextHolder.getContext().getAuthentication();
            policy.authorize(auth!=null && auth.getPrincipal() instanceof CurrentAccount actor?actor:null);
        } catch (ApiException ex) {
            errors.write(request,response,ex.status().value(),ex.codigo(),ex.getMessage()); return;
        }
        if (!scans.tryAcquire()) {
            errors.write(request,response,429,"ESCANEO_LIMITE","Hay un documento en análisis. Probá en unos segundos."); return;
        }
        java.util.UUID owner=null;
        int outcome=500;
        CloudScanTelemetry.begin();
        try {
            owner=budget.acquire();
            CloudScanPermit.bind(budget,owner);
            chain.doFilter(request,response);
            outcome=response.getStatus();
        } catch (ApiException ex) {
            errors.write(request,response,ex.status().value(),ex.codigo(),ex.getMessage());
            outcome=ex.status().value();
        } finally {
            CloudScanPermit.clear();
            if(owner!=null) try { budget.release(owner); } catch(RuntimeException ex) {
                // A failed release keeps the persistent lease closed until expiry; no private exception text.
                LoggerFactory.getLogger(CloudRequestGuard.class).error("cloud_scan lease_release_failed");
            }
            CloudScanTelemetry.finish(outcome);
            scans.release();
        }
    }
}
