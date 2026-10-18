package ar.edu.uade.medlink.dtos;

import ar.edu.uade.medlink.model.enums.CondicionDispensa;

public record PresentacionRequestDTO(
    Long idMedicamento,
    String concentracion,
    String formaFarmaceutica,
    String cantidadEnvase,
    String unidadExpendio,
    CondicionDispensa condicionDispensa
) {}
