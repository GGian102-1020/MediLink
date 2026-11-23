package ar.edu.uade.medlink.auth;

import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PasswordRecoveryService {
    private final JdbcTemplate db;
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final RecoveryMailer mail;
    private final Clock clock;
    private final AuditService audit;
    private final TransactionTemplate transaction;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Integer> requests = new HashMap<>();
    private long minute = Long.MIN_VALUE;

    public PasswordRecoveryService(JdbcTemplate db, AccountRepository accounts, PasswordEncoder passwords,
        RecoveryMailer mail, Clock clock, AuditService audit, PlatformTransactionManager manager) {
        this.db=db; this.accounts=accounts; this.passwords=passwords; this.mail=mail;
        this.clock=clock; this.audit=audit; this.transaction=new TransactionTemplate(manager);
    }

    private synchronized void limit(String client) {
        long now = Math.floorDiv(clock.instant().getEpochSecond(), 60);
        if (now != minute) { requests.clear(); minute=now; }
        if (requests.size() >= 10_000 || requests.getOrDefault(client, 0) >= 10)
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RECOVERY_LIMIT",
                "Demasiados intentos. Esperá un minuto y volvé a probar.");
        requests.merge(client, 1, Integer::sum);
    }

    public void request(String rawEmail, HttpServletRequest request) {
        String email = AuthService.email(rawEmail).toLowerCase(Locale.ROOT);
        limit(request.getRemoteAddr());
        if (!mail.configured()) throw unavailable();
        try {
            transaction.executeWithoutResult(status -> accounts.byEmail(email).ifPresent(found -> {
                var account = accounts.byIdLocked(found.id());
                if (!"ACTIVA".equals(account.estado())) return;
                var now=clock.instant();
                var created=db.query("SELECT creado FROM recuperacion_contrasena WHERE id_cuenta=?",
                    (r,n)->r.getTimestamp(1).toInstant(), account.id());
                if (!created.isEmpty() && created.getFirst().plusSeconds(60).isAfter(now)) return;
                String code=String.format(Locale.ROOT, "%08d", random.nextInt(100_000_000));
                db.update("INSERT INTO recuperacion_contrasena (id_cuenta,codigo_hash,creado,vence,token_version) "
                    + "VALUES (?,?,?,?,?) ON CONFLICT (id_cuenta) DO UPDATE SET codigo_hash=excluded.codigo_hash, "
                    + "creado=excluded.creado,vence=excluded.vence,intentos=0,token_version=excluded.token_version",
                    account.id(), hash(email,code), Timestamp.from(now), Timestamp.from(now.plusSeconds(900)), account.tokenVersion());
                mail.send(account.correo(), code);
            }));
        } catch (MailException ex) { throw unavailable(); }
    }

    public void reset(String rawEmail, String code, String password, HttpServletRequest request) {
        String email=AuthService.email(rawEmail).toLowerCase(Locale.ROOT);
        limit(request.getRemoteAddr());
        AuthService.initialPassword(password);
        if (code==null || !code.matches("[0-9]{8}")) throw invalid();
        // La transacción devuelve el resultado para conservar el contador incluso si el código falla.
        Boolean changed=transaction.execute(status -> {
            var found=accounts.byEmail(email);
            if (found.isEmpty()) return false;
            var account=accounts.byIdLocked(found.get().id());
            var rows=db.query("SELECT codigo_hash,vence,intentos,token_version FROM recuperacion_contrasena WHERE id_cuenta=?",
                (r,n)->new Challenge(r.getString(1),r.getTimestamp(2).toInstant(),r.getInt(3),r.getLong(4)), account.id());
            if (rows.isEmpty() || !"ACTIVA".equals(account.estado())) return false;
            var challenge=rows.getFirst();
            if (!challenge.expires().isAfter(clock.instant()) || challenge.attempts()>=5
                || challenge.version()!=account.tokenVersion()) return false;
            if (!MessageDigest.isEqual(challenge.hash().getBytes(StandardCharsets.US_ASCII),
                hash(email,code).getBytes(StandardCharsets.US_ASCII))) {
                db.update("UPDATE recuperacion_contrasena SET intentos=intentos+1 WHERE id_cuenta=?", account.id());
                return false;
            }
            accounts.updatePassword(account.id(), passwords.encode(password));
            db.update("DELETE FROM recuperacion_contrasena WHERE id_cuenta=?", account.id());
            Long userId="USUARIO".equals(account.rol()) ? accounts.userProfile(account.id()).usuarioId() : null;
            audit.record(account.id(),account.rol(),"CAMBIAR_CONTRASENA","CUENTA",account.id(),userId,null,
                Map.of("tokenVersion",account.tokenVersion()),Map.of("tokenVersion",account.tokenVersion()+1),request);
            return true;
        });
        if (!Boolean.TRUE.equals(changed)) throw invalid();
    }
    private record Challenge(String hash, java.time.Instant expires, int attempts, long version) { }
    private static String hash(String email,String code) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest((email + ":" + code).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST,"RECOVERY_INVALID",
            "El código es incorrecto, venció o ya fue utilizado. Solicitá uno nuevo.");
    }
    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"RECOVERY_UNAVAILABLE",
            "No se pudo enviar el correo de recuperación. Intentá más tarde.");
    }
}
