package ar.edu.uade.medlink.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
}
