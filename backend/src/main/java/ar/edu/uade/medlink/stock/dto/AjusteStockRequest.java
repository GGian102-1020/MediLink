package ar.edu.uade.medlink.stock.dto;

import jakarta.validation.constraints.*;
import ar.edu.uade.medlink.common.error.ApiException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

public record AjusteStockRequest(@NotNull @Min(0) Integer cantidadDisponible,
                                 @NotBlank @Size(max=500) String motivo) {
    public AjusteStockRequest { motivo=motivo==null?null:motivo.strip(); }

    // Scope strict quantity parsing to this command, without changing other modules' JSON configuration.
    @JsonCreator
    public static AjusteStockRequest fromJson(@JsonProperty("cantidadDisponible") JsonNode cantidad,
                                              @JsonProperty("motivo") String motivo) {
        if (cantidad==null || cantidad.isNull()) return new AjusteStockRequest(null,motivo);
        if (!cantidad.isIntegralNumber() || !cantidad.canConvertToInt())
            throw ApiException.validation("La cantidad disponible debe ser un entero no negativo.");
        return new AjusteStockRequest(cantidad.intValue(),motivo);
    }
}
