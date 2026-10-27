package ar.edu.uade.medlink.suspension.dto;

/** REST SuspensionDTO in section 11.3: no user identity or persisted status exposed. */
public record SuspensionResponseDTO(long id, String fechaHoraInicio, String fechaHoraFin,
                                    boolean vigente, String motivo) { }
