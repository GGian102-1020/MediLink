package ar.edu.uade.medlink.prescripcion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record PrescripcionCreateRequest(
    @NotBlank @Size(max=80) String codigoExterno,
    @NotNull Long usuarioId,
    @NotNull LocalDate fechaEmision,
    @NotNull LocalDate fechaVencimiento,
    @NotEmpty List<@Valid @NotNull Item> items
) {
    public PrescripcionCreateRequest {
        codigoExterno = codigoExterno == null ? null : codigoExterno.strip();
    }
    public record Item(@NotNull Long presentacionId, @NotNull @Positive Integer cantidadAutorizada) { }
}
