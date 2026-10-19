package ar.edu.uade.medlink.security;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.common.error.ErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(BearerAuthenticationFilter.class);
    private final JwtTokens tokens;
    private final AccountRepository accounts;
    private final ErrorResponses errors;
    public BearerAuthenticationFilter(JwtTokens tokens, AccountRepository accounts, ErrorResponses errors) {
        this.tokens = tokens; this.accounts = accounts; this.errors = errors;
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.equals("/api/auth/register") || uri.equals("/api/auth/login")) return true;
        // A malformed or expired Bearer header must not prevent public browsing.
        return PublicApiRoutes.publicGet(request.getMethod(), uri);
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null) { chain.doFilter(request, response); return; }
        try {
            if (!header.startsWith("Bearer ") || header.length() <= 7 || header.substring(7).contains(" ")) {
                throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_TOKEN","Formato de autorización inválido.");
            }
            Jwt jwt = tokens.verify(header.substring(7));
            long id;
            try { id = Long.parseLong(jwt.getSubject()); }
            catch (Exception ex) { throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_TOKEN","Token inválido."); }
            var account = accounts.byId(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                "INVALID_TOKEN", "Token inválido."));
            if (!"ACTIVA".equals(account.estado())) {
                throw new ApiException(HttpStatus.FORBIDDEN,"ACCOUNT_DISABLED","La cuenta está deshabilitada.");
            }
            Object version = jwt.getClaim("tokenVersion");
            String rol = jwt.getClaimAsString("rol");
            String jti = jwt.getId();
            if (!(version instanceof Number) || ((Number) version).longValue() != account.tokenVersion()
                || jti == null || jti.isBlank() || !account.rol().equals(rol) || accounts.isRevoked(jti)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_TOKEN","Token inválido, expirado o revocado.");
            }
            var current = new CurrentAccount(id, rol, jti, account.tokenVersion());
            var auth = new UsernamePasswordAuthenticationToken(current, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (ApiException ex) {
            SecurityContextHolder.clearContext();
            errors.write(request,response,ex.status().value(),ex.codigo(),ex.getMessage());
            return;
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
            // No registrar el token ni los datos de la solicitud.
            log.error("Fallo al autenticar: correlationId={} tipo={}",
                errors.correlationId(request), ex.getClass().getName());
            errors.write(request,response,500,"INTERNAL_ERROR","No fue posible completar la operación.");
            return;
        }
        // No interceptar excepciones de controladores/servicios como si fueran
        // errores al validar JWT. Spring MVC/Security deben manejarlas.
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
