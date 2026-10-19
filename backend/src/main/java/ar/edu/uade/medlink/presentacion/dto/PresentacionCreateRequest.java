package ar.edu.uade.medlink.presentacion.dto;

import ar.edu.uade.medlink.presentacion.entity.CondicionDispensa;
import jakarta.validation.constraints.*;

public record PresentacionCreateRequest(
    @NotNull Long medicamentoId,
    @NotBlank @Size(max=80) String concentracionTexto,
    @NotBlank @Size(max=80) String formaFarmaceutica,
    @NotNull @Positive Integer cantidadEnvase,
    @NotBlank @Size(max=80) String unidadExpendio,
    @NotNull CondicionDispensa condicionDispensa
) {
    public PresentacionCreateRequest {
        // CV-09: collapse repeated spaces before Bean Validation's 80-char limit.
        concentracionTexto = normalizeText(concentracionTexto);
        formaFarmaceutica = normalizeText(formaFarmaceutica);
        unidadExpendio = normalizeText(unidadExpendio);
    }
    private static String normalizeText(String value) {
        return value == null ? null : value.strip().replaceAll("\\s+", " ");
    }
}
