package ar.edu.uade.medlink.auth;

import ar.edu.uade.medlink.auth.AuthDtos.*;
import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import ar.edu.uade.medlink.security.JwtTokens;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    // Validate after trimming (CV-06), not before; domains and local parts use
    // a practical ASCII email subset appropriate to the MVP contract.
    private static final java.util.regex.Pattern EMAIL = java.util.regex.Pattern.compile(
        "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$");
    private final AccountRepository repository;
    private final PasswordEncoder passwords;
    private final JwtTokens tokens;
    private final AuditService audit;
    private final Clock clock;
    public AuthService(AccountRepository repository, PasswordEncoder passwords, JwtTokens tokens,
                       AuditService audit, Clock clock) {
        this.repository=repository; this.passwords=passwords; this.tokens=tokens;
        this.audit=audit; this.clock=clock;
    }

    public static String email(String raw) {
        String email = raw == null ? "" : raw.strip();
        if (email.isEmpty() || email.length()>254 || !EMAIL.matcher(email).matches())
            throw ApiException.validation("Correo inválido.");
        return email;
    }
    public static String name(String raw) {
        if (raw == null || raw.strip().isEmpty() || raw.strip().length()>80)
            throw ApiException.validation("Nombre o apellido inválido.");
        return raw.strip();
    }
    public static void initialPassword(String password) {
        if (password == null || password.codePointCount(0,password.length())<8
            || password.codePointCount(0,password.length())>72
            || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw ApiException.validation("La contraseña debe tener entre 8 y 72 caracteres (máximo 72 bytes UTF-8 para BCrypt).");
    }
    @Transactional
    public UsuarioPerfilDTO register(RegisterRequest request) {
        String correo = email(request.correo());
        String nombre = name(request.nombre());
        String apellido = name(request.apellido());
        initialPassword(request.contrasena());
        if (repository.byEmail(correo).isPresent())
            throw ApiException.conflict("EMAIL_ALREADY_EXISTS","Ya existe una cuenta con ese correo.");
        try {
            long id = repository.newAccount(correo,passwords.encode(request.contrasena()),"USUARIO",clock.instant());
            repository.newUser(id,nombre,apellido);
            return repository.userProfile(id);
        } catch (DuplicateKeyException ex) {
            throw ApiException.conflict("EMAIL_ALREADY_EXISTS","Ya existe una cuenta con ese correo.");
        }
    }
    // BCrypt uses at most 72 UTF-8 bytes. Apply the same byte limit to every
    // supplied password, including login and the current password on change.
    private static void suppliedPassword(String password, String message) {
        if (password == null || password.isEmpty()
            || password.codePointCount(0,password.length()) > 72
            || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw ApiException.validation(message);
    }

    @Transactional(readOnly=true)
    public LoginResponse login(LoginRequest request) {
        String correo = email(request.correo());
        suppliedPassword(request.contrasena(), "Contraseña inválida.");
        var account = repository.byEmail(correo).orElseThrow(AuthService::invalidCredentials);
        if (!passwords.matches(request.contrasena(),account.hash())) throw invalidCredentials();
        if (!"ACTIVA".equals(account.estado()))
            throw new ApiException(HttpStatus.FORBIDDEN,"ACCOUNT_DISABLED","La cuenta está deshabilitada.");
        return new LoginResponse(tokens.create(account), "Bearer",JwtTokens.TTL_SECONDS,repository.perfil(account.id()));
    }
    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Correo o contraseña incorrectos.");
    }
    @Transactional
    public void logout(CurrentAccount current, String bearerToken) {
        // The authenticated filter already checked signature, expiration, version and jti.
        var jwt = tokens.verify(bearerToken);
        if (!current.jti().equals(jwt.getId()) || !Long.toString(current.id()).equals(jwt.getSubject())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_TOKEN","Token inválido.");
        }
        repository.revoke(current.jti(),current.id(),jwt.getExpiresAt());
    }
    @Transactional
    public void changePassword(CurrentAccount current, ChangePasswordRequest request,
                               HttpServletRequest httpRequest) {
        suppliedPassword(request.contrasenaActual(), "Contraseña actual inválida.");
        initialPassword(request.nuevaContrasena());
        var account = repository.byIdLocked(current.id());
        if (account.tokenVersion()!=current.tokenVersion())
            throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_TOKEN","La sesión ya no es válida.");
        if (!"ACTIVA".equals(account.estado()))
            throw new ApiException(HttpStatus.FORBIDDEN,"ACCOUNT_DISABLED","La cuenta está deshabilitada.");
        if (!passwords.matches(request.contrasenaActual(),account.hash()))
            throw new ApiException(HttpStatus.UNAUTHORIZED,"CURRENT_PASSWORD_INVALID","Contraseña actual incorrecta.");
        if (passwords.matches(request.nuevaContrasena(),account.hash()))
            throw ApiException.validation("La contraseña nueva debe ser distinta a la actual.");
        repository.updatePassword(current.id(),passwords.encode(request.nuevaContrasena()));
        Long relatedUserId = "USUARIO".equals(current.rol()) ? repository.userProfile(current.id()).usuarioId() : null;
        audit.record(current.id(),current.rol(),"CAMBIAR_CONTRASENA","CUENTA",current.id(),relatedUserId,null,
            Map.of("tokenVersion", account.tokenVersion()), Map.of("tokenVersion",account.tokenVersion()+1),httpRequest);
    }
    @Transactional(readOnly=true)
    public MeDTO me(CurrentAccount current) {
        CuentaPerfilDTO perfil=repository.perfil(current.id());
        if (!"USUARIO".equals(current.rol())) return new MeDTO(perfil,null);
        long userId = perfil.perfilId();
        java.time.Instant t=clock.instant();
        var suspensions = repository.jdbc().query(
            "SELECT id_suspension, fecha_hora_inicio, fecha_hora_fin FROM suspension WHERE id_usuario=? " +
            "AND fecha_hora_inicio <= ? AND fecha_hora_fin > ? ORDER BY fecha_hora_inicio DESC LIMIT 1",
            (r,n)->new SuspensionDTO(r.getLong(1),
                ApiClock.argentina(r.getObject(2,OffsetDateTime.class).toInstant()),
                ApiClock.argentina(r.getObject(3,OffsetDateTime.class).toInstant()),true,"INCUMPLIMIENTOS_REITERADOS"),
            userId,Timestamp.from(t),Timestamp.from(t));
        return new MeDTO(perfil,suspensions.stream().findFirst().orElse(null));
    }
}
