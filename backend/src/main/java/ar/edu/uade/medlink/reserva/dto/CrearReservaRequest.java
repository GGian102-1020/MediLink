package ar.edu.uade.medlink.reserva.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CrearReservaRequest(@NotNull Long farmaciaId, @NotNull Long presentacionId,
                                  @NotNull @Min(1) Integer cantidad, String codigoPrescripcion) { }
