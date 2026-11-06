package ar.edu.uade.medlink.farmacia.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record FarmaciaRequest(
    @NotBlank @Size(max=120) String nombre,
    @NotBlank @Size(max=200) String direccion,
    @Size(max=30) String telefono,
    @Email @Size(max=254) String emailContacto,
    @DecimalMin(value="-90.0") @DecimalMax(value="90.0")  BigDecimal latitud,
    @DecimalMin(value="-180.0") @DecimalMax(value="180.0") BigDecimal longitud) {
    public FarmaciaRequest {
        nombre    = nombre    == null ? null : nombre.strip();
        direccion = direccion == null ? null : direccion.strip();
        telefono      = optional(telefono);
        emailContacto = optional(emailContacto);
    }
    // Permite sin geolocalizacion, pero no un punto con un solo eje.
    @AssertTrue(message = "latitud y longitud deben informarse juntas")
    public boolean isUbicacionCompleta() {
        return (latitud == null) == (longitud == null);
    }
    private static String optional(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
