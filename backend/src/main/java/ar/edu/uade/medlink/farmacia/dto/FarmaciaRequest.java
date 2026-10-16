package ar.edu.uade.medlink.farmacia.dto;

import jakarta.validation.constraints.*;

public record FarmaciaRequest(
    @NotBlank @Size(max=120) String nombre,
    @NotBlank @Size(max=200) String direccion,
    @Size(max=30) String telefono,
    @Email @Size(max=254) String emailContacto) {
    public FarmaciaRequest {
        nombre=nombre==null?null:nombre.strip();
        direccion=direccion==null?null:direccion.strip();
        telefono=optional(telefono); emailContacto=optional(emailContacto);
    }
    private static String optional(String text) {
        return text==null||text.isBlank()?null:text.strip();
    }
}
