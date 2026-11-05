package ar.edu.uade.medlink.stock.dto;

import java.math.BigDecimal;

/**
 * Farmacia con stock de una Presentacion.
 * latitud/longitud son null si la farmacia no esta geolocalizada;
 * distanciaKm solo se informa cuando el pedido incluye la ubicacion del usuario.
 */
public record DisponibilidadFarmaciaDTO(long farmaciaId, String nombre, String direccion, int cantidadDisponible,
                                        BigDecimal latitud, BigDecimal longitud, Double distanciaKm) { }
