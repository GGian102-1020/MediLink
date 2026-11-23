package ar.edu.uade.medlink.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class AuthDtos {
    private AuthDtos() { }

    public record RegisterRequest(
        @NotBlank String nombre,
        @NotBlank String apellido,
        @NotBlank String correo,
        @NotNull String contrasena,
        /** 7 u 8 dígitos; se aceptan puntos o espacios y se descartan. */
        String dni,
        /** ISO yyyy-MM-dd; el paciente debe ser mayor de 18 años. */
        String fechaNacimiento,
        String telefono,
        String obraSocial,
        String numeroAfiliado) { }

    /** Datos personales ya normalizados y validados que se guardan en la tabla usuario. */
    public record DatosPersonales(String dni, java.time.LocalDate fechaNacimiento, String telefono,
                                  String obraSocial, String numeroAfiliado) { }

    public record LoginRequest(
        @NotBlank String correo,
        @NotNull String contrasena) { }

    public record ChangePasswordRequest(
        @NotNull String contrasenaActual,
        @NotNull String nuevaContrasena) { }

    public record CuentaPerfilDTO(long cuentaId, long perfilId, String rol, String correo,
                                  String estadoCuenta, String nombre, String apellido, Long farmaciaId) { }
    public record UsuarioPerfilDTO(long usuarioId, long cuentaId, String nombre, String apellido,
                                   String correo, String estadoCuenta, String rol) { }
    public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds,
                                CuentaPerfilDTO perfil) { }
    public record SuspensionDTO(long id, java.time.OffsetDateTime fechaHoraInicio,
                                java.time.OffsetDateTime fechaHoraFin, boolean vigente, String motivo) { }
    public record MeDTO(CuentaPerfilDTO perfil, SuspensionDTO suspensionVigente) { }
}
