package ar.edu.uade.medlink.medicamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MedicamentoRequest(@NotBlank @Size(max=160) String nombre) {
    // CV-09 / DTO commands: limits apply to domain text after trim, not to raw input.
    public MedicamentoRequest {
        nombre = nombre == null ? null : nombre.strip();
    }
}
