package ar.edu.uade.medlink.auth;

import ar.edu.uade.medlink.auth.AuthDtos.CuentaPerfilDTO;
import ar.edu.uade.medlink.auth.AuthDtos.UsuarioPerfilDTO;
import ar.edu.uade.medlink.auth.AuthDtos.SuspensionDTO;
import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.error.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    public record Account(long id, String correo, String hash, String rol, String estado, long tokenVersion) { }
    private final JdbcTemplate db;
    public AccountRepository(JdbcTemplate db) { this.db = db; }

    private static final org.springframework.jdbc.core.RowMapper<Account> ACCOUNT = (r, n) ->
        new Account(r.getLong("id_cuenta"), r.getString("correo"), r.getString("password_hash"),
            r.getString("rol"), r.getString("estado"), r.getLong("token_version"));
    private static final String SELECT = "SELECT id_cuenta, correo, password_hash, rol::text AS rol, " +
        "estado::text AS estado, token_version FROM cuenta";

    public Optional<Account> byEmail(String email) {
        return db.query(SELECT + " WHERE correo_normalizado = lower(btrim(?))", ACCOUNT, email)
            .stream().findFirst();
    }
    public Optional<Account> byId(long id) {
        return db.query(SELECT + " WHERE id_cuenta = ?", ACCOUNT, id).stream().findFirst();
    }
    public Account byIdLocked(long id) {
        return db.query(SELECT + " WHERE id_cuenta = ? FOR UPDATE", ACCOUNT, id)
            .stream().findFirst().orElseThrow(ApiException::notFound);
    }
    public long newAccount(String email, String hash, String rol, Instant instant) {
        return db.queryForObject("INSERT INTO cuenta (correo, password_hash, rol, estado, fecha_hora_creacion) " +
              "VALUES (?, ?, ?::rol_cuenta, 'ACTIVA', ?) RETURNING id_cuenta",
            Long.class, email, hash, rol, Timestamp.from(instant));
    }
    public long newUser(long accountId, String nombre, String apellido) {
        return db.queryForObject("INSERT INTO usuario (id_cuenta, nombre, apellido) VALUES (?, ?, ?) RETURNING id_usuario",
            Long.class, accountId, nombre, apellido);
    }
    public CuentaPerfilDTO perfil(long id) {
        String sql = "SELECT c.id_cuenta, c.correo, c.rol::text AS rol, c.estado::text AS estado, " +
            "COALESCE(u.id_usuario,o.id_operador,a.id_administrador) AS perfil_id, " +
            "COALESCE(u.nombre,o.nombre,a.nombre) AS nombre, " +
            "COALESCE(u.apellido,o.apellido,a.apellido) AS apellido, o.id_farmacia " +
            "FROM cuenta c LEFT JOIN usuario u ON u.id_cuenta=c.id_cuenta " +
            "LEFT JOIN operador o ON o.id_cuenta=c.id_cuenta " +
            "LEFT JOIN administrador a ON a.id_cuenta=c.id_cuenta WHERE c.id_cuenta=?";
        return db.query(sql, (r,n) -> new CuentaPerfilDTO(r.getLong("id_cuenta"), r.getLong("perfil_id"),
            r.getString("rol"), r.getString("correo"), r.getString("estado"), r.getString("nombre"),
            r.getString("apellido"), (Long) r.getObject("id_farmacia")), id)
            .stream().findFirst().orElseThrow(ApiException::notFound);
    }
    public UsuarioPerfilDTO userProfile(long cuentaId) {
        return db.query("SELECT u.id_usuario,u.id_cuenta,u.nombre,u.apellido,c.correo,c.estado::text AS estado " +
            "FROM usuario u JOIN cuenta c ON c.id_cuenta=u.id_cuenta WHERE c.id_cuenta=?",
            (r,n)->new UsuarioPerfilDTO(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),
                r.getString(5),r.getString(6),"USUARIO"), cuentaId)
            .stream().findFirst().orElseThrow(ApiException::notFound);
    }
    public void updatePassword(long id, String hash) {
        db.update("UPDATE cuenta SET password_hash=?,token_version=token_version+1 WHERE id_cuenta=?", hash,id);
    }
    public void changeState(long id, String estado, boolean invalidate) {
        db.update("UPDATE cuenta SET estado=?::estado_cuenta, token_version=token_version+? WHERE id_cuenta=?",
            estado, invalidate ? 1 : 0, id);
    }
    public boolean isRevoked(String jti) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS (SELECT 1 FROM sesion_revocada WHERE jti=?)",
            Boolean.class, jti));
    }
    public void revoke(String jti, long id, Instant expires) {
        db.update("INSERT INTO sesion_revocada (jti,id_cuenta,fecha_hora_expiracion) " +
            "VALUES (?,?,?) ON CONFLICT (jti) DO NOTHING", jti, id, Timestamp.from(expires));
    }
    public Optional<SuspensionDTO> activeSuspension(long userId, Instant instant) {
        var matches = db.query(
            "SELECT id_suspension, fecha_hora_inicio, fecha_hora_fin FROM suspension WHERE id_usuario=? " +
            "AND fecha_hora_inicio <= ? AND fecha_hora_fin > ? ORDER BY fecha_hora_inicio DESC LIMIT 1",
            (r,n) -> new SuspensionDTO(r.getLong(1),
                ApiClock.argentina(r.getObject(2, OffsetDateTime.class).toInstant()),
                ApiClock.argentina(r.getObject(3, OffsetDateTime.class).toInstant()),
                true, "INCUMPLIMIENTOS_REITERADOS"),
            userId, Timestamp.from(instant), Timestamp.from(instant));
        return matches.stream().findFirst();
    }
}
