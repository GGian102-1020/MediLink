package ar.edu.uade.medlink.farmacia.dto;

import java.math.BigDecimal;

/**
 * Proyección de farmacia con la distancia calculada (Haversine) en kilómetros.
 */
public record FarmaciaCercanaDTO(
        long id,
        String nombre,
        String direccion,
        String telefono,
        BigDecimal latitud,
        BigDecimal longitud,
        double distanciaKm) { }
