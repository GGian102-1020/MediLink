package ar.edu.uade.medlink.recetademo;

import ar.edu.uade.medlink.prescripcion.dto.PrescripcionDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RecetaDemoDtos {
    private RecetaDemoDtos() { }

    public record GenerarLote(@NotNull @Positive Long presentacionId,
                             @NotNull @Min(1) @Max(100) Integer cantidad,
                             @NotNull LocalDate fechaVencimiento) { }
    public record Receta(long id, String codigoReceta, String estado, LocalDate fechaEmision,
                         LocalDate fechaVencimiento, long presentacionId, String medicamento,
                         String concentracion, String presentacion, int cantidadAutorizada) { }
    public record Lote(UUID lote, List<Receta> recetas) { }
    public record Vincular(@NotBlank @Size(max=64) String codigoReceta) { }
    public enum Verificacion { RECETA_VERIFICADA, RECETA_VERIFICADA_LEGADA }
    public record Vinculacion(boolean nueva, PrescripcionDTO prescripcion,
                              @JsonInclude(JsonInclude.Include.NON_NULL) Verificacion codigo) { }
}
