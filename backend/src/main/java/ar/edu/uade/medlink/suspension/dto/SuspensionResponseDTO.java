package ar.edu.uade.medlink.suspension.dto;

import ar.edu.uade.medlink.suspension.entity.EstadoSuspension;
import java.time.LocalDateTime;

public record SuspensionResponseDTO(
    Long idSuspension,
    Long idPaciente,
    String nombrePaciente,
    LocalDateTime fechaInicio,
    LocalDateTime fechaFin,
    String motivo,
    EstadoSuspension estado
) {}
