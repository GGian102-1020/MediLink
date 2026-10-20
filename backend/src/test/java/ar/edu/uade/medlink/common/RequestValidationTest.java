package ar.edu.uade.medlink.common;

import ar.edu.uade.medlink.common.error.ApiException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestValidationTest {
    @Test void reasonHasSingleSharedRule() {
        assertEquals("Inventario",RequestValidation.requiredReason(" Inventario "));
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->RequestValidation.requiredReason("  ")).codigo());
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->RequestValidation.requiredReason("X".repeat(501))).codigo());
    }
}
