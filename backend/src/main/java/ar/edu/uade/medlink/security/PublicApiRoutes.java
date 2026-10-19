package ar.edu.uade.medlink.security;

import org.springframework.util.AntPathMatcher;

/** Public GET routes must stay identical in the HTTP security chain and Bearer filter. */
public final class PublicApiRoutes {
    private PublicApiRoutes() { }
    public static final String[] GET_PATTERNS = {
        "/api/health", "/api/medicamentos", "/api/medicamentos/*/presentaciones",
        "/api/presentaciones/*", "/api/presentaciones/*/disponibilidad"
    };
    private static final AntPathMatcher PATHS = new AntPathMatcher();

    public static boolean publicGet(String method, String uri) {
        if (!"GET".equalsIgnoreCase(method)) return false;
        for (String pattern : GET_PATTERNS) if (PATHS.match(pattern, uri)) return true;
        return false;
    }
}
