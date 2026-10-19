package ar.edu.uade.medlink.medicamento.dto;

import ar.edu.uade.medlink.medicamento.entity.EstadoMedicamento;

public record MedicamentoDTO(long id, String nombre, EstadoMedicamento estado) { }
