package ar.edu.uade.medlink.farmacia.dto;

import ar.edu.uade.medlink.farmacia.entity.EstadoFarmacia;
import java.math.BigDecimal;

public record FarmaciaDTO(long id, String nombre, String direccion, String telefono,
                          String emailContacto, BigDecimal latitud, BigDecimal longitud,
                          EstadoFarmacia estado, boolean operativa) { }
