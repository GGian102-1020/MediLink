package ar.edu.uade.medlink.dtos;

import ar.edu.uade.medlink.model.enums.CondicionDispensa;

public record PresentacionResponseDTO(
    Long idPresentacion,
    Long idMedicamento,
    String nombreMedicamento,
    String concentracion,
    String formaFarmaceutica,
    String cantidadEnvase,
    String unidadExpendio,
    CondicionDispensa condicionDispensa,
    boolean activo
) {}