package ar.edu.uade.medlink.reserva.dto;

import jakarta.validation.constraints.NotNull;

public record CancelacionOperativaRequest(@NotNull String causa, String motivo,
                                          String restitucionStock, Integer cantidadStockReal) { }
