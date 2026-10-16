package ar.edu.uade.medlink.farmacia.dto;

import ar.edu.uade.medlink.farmacia.entity.EstadoFarmacia;

public record FarmaciaDTO(long id, String nombre, String direccion, String telefono,
                          String emailContacto, EstadoFarmacia estado, boolean operativa) { }
