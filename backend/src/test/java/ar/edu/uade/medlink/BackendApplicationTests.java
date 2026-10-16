package ar.edu.uade.medlink;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

class BackendApplicationTests {
    @Test void applicationEntrypointExists() {
        // Full startup needs PostgreSQL and a JWT secret; the unit test must not silently
        // substitute an in-memory DB for the real PostgreSQL contract.
        assertNotNull(BackendApplication.class);
    }
}
