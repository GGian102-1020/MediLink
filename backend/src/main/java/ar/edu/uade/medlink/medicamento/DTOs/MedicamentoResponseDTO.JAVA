package ar.edu.uade.medlink.medicamento.dto;

public record MedicamentoResponseDTO(
    Long idMedicamento,
    String nombre,
    String principioActivo,
    boolean activo
) {}