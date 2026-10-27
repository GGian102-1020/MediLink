package ar.edu.uade.medlink.suspension.dto;

import java.time.LocalDateTime;

public record SuspensionRequestDTO(
    Long idPaciente,
    LocalDateTime fechaFin,
    String motivo
) {}
