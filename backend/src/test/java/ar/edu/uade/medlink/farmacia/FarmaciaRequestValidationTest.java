package ar.edu.uade.medlink.farmacia;

import ar.edu.uade.medlink.farmacia.dto.FarmaciaRequest;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmaciaRequestValidationTest {
    @Test
    void permiteAmbasCoordenadasOAunqueNoEstan() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var sinMapa = new FarmaciaRequest("Demo", "CABA", null, null, null, null);
            var conMapa = new FarmaciaRequest("Demo", "CABA", null, null,
                new BigDecimal("-34.60"), new BigDecimal("-58.40"));
            var soloLatitud = new FarmaciaRequest("Demo", "CABA", null, null,
                new BigDecimal("-34.60"), null);
            var latitudFueraDeRango = new FarmaciaRequest("Demo", "CABA", null, null,
                new BigDecimal("91"), new BigDecimal("-58.40"));
            assertTrue(validator.validate(sinMapa).isEmpty());
            assertTrue(validator.validate(conMapa).isEmpty());
            assertFalse(validator.validate(soloLatitud).isEmpty());
            assertFalse(validator.validate(latitudFueraDeRango).isEmpty());
        }
    }
}
