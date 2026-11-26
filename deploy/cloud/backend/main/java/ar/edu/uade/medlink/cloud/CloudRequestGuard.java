package ar.edu.uade.medlink.cloud;

import ar.edu.uade.medlink.common.error.ErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Cloud abuse protection; prescription comparison and business rules are unchanged. */
@Component
@Profile("cloud")
@Order(Ordered.LOWEST_PRECEDENCE)
public class CloudRequestGuard extends OncePerRequestFilter {
    private final ErrorResponses errors;
    private final JdbcTemplate db;
    private final Clock clock;
    private final int dailyLimit;
    private final Semaphore scans = new Semaphore(1);
    private final Map<String,Integer> requests = new HashMap<>();
    private long window = Long.MIN_VALUE;
    private int total;
    public CloudRequestGuard(ErrorResponses errors, JdbcTemplate db, Clock clock,
        @Value("${medlink.cloud.scan-daily-limit:20}") int dailyLimit) {
        if (dailyLimit < 1 || dailyLimit > 100) throw new IllegalArgumentException("Invalid daily scan limit");
        this.errors=errors; this.db=db; this.clock=clock; this.dailyLimit=dailyLimit;
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
        if (!scans.tryAcquire()) {
            errors.write(request,response,429,"ESCANEO_LIMITE","Hay un documento en análisis. Probá en unos segundos."); return;
        }
        try {
            var claimed=db.query("INSERT INTO cloud_scan_budget(usage_date,attempts) VALUES (?,1) "
                +"ON CONFLICT(usage_date) DO UPDATE SET attempts=cloud_scan_budget.attempts+1 "
                +"WHERE cloud_scan_budget.attempts < ? RETURNING attempts",
                (r,n)->r.getInt(1),LocalDate.now(clock.withZone(ZoneOffset.UTC)),dailyLimit);
            if (claimed.isEmpty()) {
                errors.write(request,response,429,"ESCANEO_LIMITE_DIARIO","Se alcanzó el límite diario de análisis."); return;
            }
            chain.doFilter(request,response);
        } finally { scans.release(); }
    }
}
