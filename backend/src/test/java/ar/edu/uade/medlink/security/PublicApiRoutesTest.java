package ar.edu.uade.medlink.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicApiRoutesTest {
    @Test void publicPatternsNeverExposeAdministrativeEndpoints() {
        for (String path: new String[]{"/api/health","/api/medicamentos",
            "/api/medicamentos/2/presentaciones","/api/presentaciones/3",
            "/api/presentaciones/3/disponibilidad"})
            assertTrue(PublicApiRoutes.publicGet("GET",path),path);
        for (String path: new String[]{"/api/admin/medicamentos","/api/admin/presentaciones",
            "/api/me","/api/presentaciones/3/desactivacion","/api/medicamentos/2/presentaciones/extra"})
            assertFalse(PublicApiRoutes.publicGet("GET",path),path);
        assertFalse(PublicApiRoutes.publicGet("POST","/api/medicamentos"));
    }
}
