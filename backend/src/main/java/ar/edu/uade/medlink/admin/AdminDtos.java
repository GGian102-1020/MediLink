package ar.edu.uade.medlink.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() { }
    public record OperadorCreateRequest(
        @NotBlank String nombre,
        @NotBlank String apellido,
        @NotBlank String correo,
        @NotNull String contrasenaInicial,
        @NotNull Long farmaciaId) { }
    public record OperadorUpdateRequest(
        @NotBlank String nombre,
        @NotBlank String apellido) { }
    public record CambioEstadoRequest(@NotBlank String motivo) { }
    public record OperadorDTO(long operadorId, long cuentaId, String nombre, String apellido,
                              String correo, String estadoCuenta, long farmaciaId, String farmaciaNombre) { }
    public record PageDTO<T>(List<T> content, int page, int size, long totalElements,
                             int totalPages, boolean first, boolean last) {
        public static <T> PageDTO<T> from(List<T> content, int page, int size, long total) {
            int pages=(int)((total+size-1)/size);
            return new PageDTO<>(content,page,size,total,pages,page==0,page>=pages-1);
        }
    }
}
