package ar.edu.uade.medlink.chat;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.chat.ChatDtos.*;
import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chat farmacia ↔ paciente. Una conversación es el par (farmacia, usuario) y solo existe si el
 * paciente tiene al menos una reserva en esa farmacia: el operador no puede escribirle a
 * cualquier paciente ni el paciente a cualquier farmacia.
 */
@Service
public class ChatService {
    static final int MAX_TEXTO = 1000;
    static final int MAX_MENSAJES = 200;
    static final int MAX_CONVERSACIONES = 50;
    static final String FARMACIA = "FARMACIA";
    static final String USUARIO = "USUARIO";
    /** Las conversaciones cerradas sin mensajes (p. ej. ya purgadas) no se listan. */
    private static final String VISIBLE = " WHERE (coalesce(rr.abierta, false) OR lm.fecha_hora IS NOT NULL)";

    /**
     * Reserva de referencia de una conversación: la abierta (ACTIVA o PENDIENTE sin vencer) más
     * reciente o, si no hay ninguna, la última. Parámetros: instante, instante. farmaciaCol y
     * usuarioCol son expresiones SQL fijas del código (columnas o "?"), nunca entrada del usuario.
     */
    /** Momento en que terminó una reserva; para una ACTIVA es su vencimiento (futuro mientras siga abierta). */
    static final String CIERRE = "coalesce(r.fecha_hora_retiro, r.fecha_hora_cancelacion, r.fecha_hora_vencimiento)";

    private static String reservaReferencia(String farmaciaCol, String usuarioCol) {
        return """
            LEFT JOIN LATERAL (
                SELECT r.codigo_visible, r.medicamento_nombre_snapshot,
                       (r.estado_persistido = 'ACTIVA' AND r.fecha_hora_vencimiento > ?) AS abierta,
                       CASE WHEN r.estado_persistido = 'ACTIVA' AND r.fecha_hora_vencimiento <= ? THEN 'VENCIDA'
                            WHEN r.estado_persistido = 'ACTIVA' AND r.requiere_confirmacion
                                 AND r.fecha_hora_confirmacion IS NULL THEN 'PENDIENTE'
                            ELSE r.estado_persistido::text END AS estado,
                       (SELECT max(coalesce(r2.fecha_hora_retiro, r2.fecha_hora_cancelacion, r2.fecha_hora_vencimiento))
                        FROM reserva r2
                        WHERE r2.id_farmacia = r.id_farmacia AND r2.id_usuario = r.id_usuario) AS cierre
                FROM reserva r WHERE r.id_farmacia = %s AND r.id_usuario = %s
                ORDER BY abierta DESC, r.id_reserva DESC LIMIT 1
            ) rr ON true
            """.formatted(farmaciaCol, usuarioCol);
    }

    private final JdbcTemplate db;
    private final AccountRepository accounts;
    private final Clock clock;
    /** Días que se conserva el historial de una conversación cerrada antes de borrarse solo. */
    private final Duration retencion;

    public ChatService(JdbcTemplate db, AccountRepository accounts, Clock clock,
                       @Value("${medlink.chat.retencion-dias:7}") int retencionDias) {
        if (retencionDias < 1) throw new IllegalArgumentException("medlink.chat.retencion-dias debe ser >= 1");
        this.db = db; this.accounts = accounts; this.clock = clock; this.retencion = Duration.ofDays(retencionDias);
    }

    /** Borrado del historial: fin de la última reserva + retención. null mientras la conversación siga abierta. */
    private OffsetDateTime borrado(ResultSet r) throws SQLException {
        Timestamp cierre = r.getTimestamp("cierre");
        return r.getBoolean("abierta") || cierre == null ? null : ApiClock.argentina(cierre.toInstant().plus(retencion));
    }

    /**
     * Borra los mensajes de las conversaciones cerradas hace más que la retención: ninguna reserva del par
     * (farmacia, paciente) sigue abierta ni terminó dentro del período. Devuelve la cantidad de mensajes borrados.
     */
    @Transactional
    public int purgarConversacionesCerradas() {
        Instant limite = ahora().minus(retencion);
        return db.update("DELETE FROM mensaje_chat m WHERE NOT EXISTS (SELECT 1 FROM reserva r " +
            "WHERE r.id_farmacia = m.id_farmacia AND r.id_usuario = m.id_usuario AND " + CIERRE + " > ?)",
            Timestamp.from(limite));
    }

    // ---------------------------------------------------------------- farmacia (operador)

    @Transactional(readOnly = true)
    public List<ConversacionFarmaciaDTO> conversacionesFarmacia(long farmacia, String busqueda, CurrentAccount actor) {
        operadorDe(farmacia, actor);
        Timestamp t = Timestamp.from(ahora());
        var args = new ArrayList<Object>(List.of(farmacia, farmacia, farmacia, t, t, farmacia));
        String filtro = "";
        if (busqueda != null && !busqueda.isBlank()) {
            filtro = " AND lower(unaccent(u.nombre || ' ' || u.apellido)) LIKE lower(unaccent(?)) ESCAPE '!'";
            args.add(like(busqueda));
        }
        args.add(MAX_CONVERSACIONES);
        String sql = """
            WITH pacientes AS (
                SELECT id_usuario, max(fecha_hora_creacion) AS ultima_reserva
                FROM reserva WHERE id_farmacia = ? GROUP BY id_usuario
            )
            SELECT u.id_usuario, u.nombre, u.apellido, lm.texto, lm.autor,
                   rr.codigo_visible, rr.medicamento_nombre_snapshot, rr.estado, rr.cierre, coalesce(rr.abierta, false) AS abierta,
                   GREATEST(lm.fecha_hora, p.ultima_reserva) AS actividad,
                   (SELECT count(*) FROM mensaje_chat m WHERE m.id_farmacia = ? AND m.id_usuario = u.id_usuario
                        AND m.autor = 'USUARIO' AND m.fecha_hora_lectura IS NULL) AS no_leidos
            FROM pacientes p
            JOIN usuario u ON u.id_usuario = p.id_usuario
            LEFT JOIN LATERAL (
                SELECT texto, autor, fecha_hora FROM mensaje_chat m
                WHERE m.id_farmacia = ? AND m.id_usuario = u.id_usuario
                ORDER BY m.id_mensaje DESC LIMIT 1
            ) lm ON true
            """ + reservaReferencia("?", "u.id_usuario") + VISIBLE + filtro + """
             ORDER BY (lm.fecha_hora IS NULL), actividad DESC, u.id_usuario
             LIMIT ?
            """;
        return db.query(sql, (r, n) -> new ConversacionFarmaciaDTO(r.getLong("id_usuario"), r.getString("nombre"),
            r.getString("apellido"), r.getString("texto"), r.getString("autor"), fecha(r, "actividad"),
            r.getLong("no_leidos"), r.getString("medicamento_nombre_snapshot"), r.getString("codigo_visible"),
            r.getString("estado"), r.getBoolean("abierta"), borrado(r)), args.toArray());
    }

    @Transactional
    public List<MensajeDTO> mensajesFarmacia(long farmacia, long usuario, Long despuesDe, CurrentAccount actor) {
        operadorDe(farmacia, actor);
        conversacionPermitida(farmacia, usuario);
        marcarLeidos(farmacia, usuario, USUARIO);
        return mensajes(farmacia, usuario, despuesDe, FARMACIA);
    }

    @Transactional
    public MensajeDTO enviarFarmacia(long farmacia, long usuario, EnviarMensajeRequest body, CurrentAccount actor) {
        operadorDe(farmacia, actor);
        conversacionPermitida(farmacia, usuario);
        requiereAbierta(farmacia, usuario, "El paciente no tiene reservas activas en tu farmacia: la conversación quedó cerrada.");
        return insertar(farmacia, usuario, FARMACIA, actor.id(), texto(body));
    }

    @Transactional(readOnly = true)
    public NoLeidosDTO noLeidosFarmacia(long farmacia, CurrentAccount actor) {
        operadorDe(farmacia, actor);
        return new NoLeidosDTO(db.queryForObject("SELECT count(*) FROM mensaje_chat WHERE id_farmacia = ? " +
            "AND autor = 'USUARIO' AND fecha_hora_lectura IS NULL", Long.class, farmacia));
    }

    // ---------------------------------------------------------------- paciente

    @Transactional(readOnly = true)
    public List<ConversacionUsuarioDTO> conversacionesUsuario(CurrentAccount actor) {
        long usuario = usuarioDe(actor);
        Timestamp t = Timestamp.from(ahora());
        String sql = """
            WITH farmacias AS (
                SELECT id_farmacia, max(fecha_hora_creacion) AS ultima_reserva
                FROM reserva WHERE id_usuario = ? GROUP BY id_farmacia
            )
            SELECT f.id_farmacia, f.nombre, f.direccion, lm.texto, lm.autor,
                   rr.codigo_visible, rr.medicamento_nombre_snapshot, rr.estado, rr.cierre, coalesce(rr.abierta, false) AS abierta,
                   GREATEST(lm.fecha_hora, x.ultima_reserva) AS actividad,
                   (SELECT count(*) FROM mensaje_chat m WHERE m.id_usuario = ? AND m.id_farmacia = f.id_farmacia
                        AND m.autor = 'FARMACIA' AND m.fecha_hora_lectura IS NULL) AS no_leidos
            FROM farmacias x
            JOIN farmacia f ON f.id_farmacia = x.id_farmacia
            LEFT JOIN LATERAL (
                SELECT texto, autor, fecha_hora FROM mensaje_chat m
                WHERE m.id_usuario = ? AND m.id_farmacia = f.id_farmacia
                ORDER BY m.id_mensaje DESC LIMIT 1
            ) lm ON true
            """ + reservaReferencia("f.id_farmacia", "?") + VISIBLE + """
            ORDER BY (lm.fecha_hora IS NULL), actividad DESC, f.id_farmacia
            LIMIT ?
            """;
        return db.query(sql, (r, n) -> new ConversacionUsuarioDTO(r.getLong("id_farmacia"), r.getString("nombre"),
            r.getString("direccion"), r.getString("texto"), r.getString("autor"), fecha(r, "actividad"),
            r.getLong("no_leidos"), r.getString("medicamento_nombre_snapshot"), r.getString("codigo_visible"),
            r.getString("estado"), r.getBoolean("abierta"), borrado(r)),
            usuario, usuario, usuario, t, t, usuario, MAX_CONVERSACIONES);
    }

    @Transactional
    public List<MensajeDTO> mensajesUsuario(long farmacia, Long despuesDe, CurrentAccount actor) {
        long usuario = usuarioDe(actor);
        conversacionPermitida(farmacia, usuario);
        marcarLeidos(farmacia, usuario, FARMACIA);
        return mensajes(farmacia, usuario, despuesDe, USUARIO);
    }

    @Transactional
    public MensajeDTO enviarUsuario(long farmacia, EnviarMensajeRequest body, CurrentAccount actor) {
        long usuario = usuarioDe(actor);
        conversacionPermitida(farmacia, usuario);
        requiereAbierta(farmacia, usuario, "Tu reserva en esta farmacia terminó: la conversación quedó cerrada.");
        return insertar(farmacia, usuario, USUARIO, actor.id(), texto(body));
    }

    @Transactional(readOnly = true)
    public NoLeidosDTO noLeidosUsuario(CurrentAccount actor) {
        long usuario = usuarioDe(actor);
        return new NoLeidosDTO(db.queryForObject("SELECT count(*) FROM mensaje_chat WHERE id_usuario = ? " +
            "AND autor = 'FARMACIA' AND fecha_hora_lectura IS NULL", Long.class, usuario));
    }

    @Transactional(readOnly = true)
    public EstadoConversacionDTO estadoFarmacia(long farmacia, long usuario, CurrentAccount actor) {
        operadorDe(farmacia, actor);
        conversacionPermitida(farmacia, usuario);
        return estado(farmacia, usuario);
    }

    @Transactional(readOnly = true)
    public EstadoConversacionDTO estadoUsuario(long farmacia, CurrentAccount actor) {
        long usuario = usuarioDe(actor);
        conversacionPermitida(farmacia, usuario);
        return estado(farmacia, usuario);
    }

    // ---------------------------------------------------------------- comunes

    EstadoConversacionDTO estado(long farmacia, long usuario) {
        Timestamp t = Timestamp.from(ahora());
        return db.query("SELECT rr.codigo_visible, rr.medicamento_nombre_snapshot, rr.estado, rr.abierta, rr.cierre " +
                "FROM (SELECT 1) x " + reservaReferencia("?", "?"),
            (r, n) -> new EstadoConversacionDTO(r.getBoolean("abierta"), r.getString("medicamento_nombre_snapshot"),
                r.getString("codigo_visible"), r.getString("estado"), borrado(r)), t, t, farmacia, usuario)
            .stream().findFirst().orElseThrow(ApiException::notFound);
    }

    /** Solo se escribe mientras el paciente tiene una reserva abierta en la farmacia; el historial sigue visible. */
    private void requiereAbierta(long farmacia, long usuario, String mensaje) {
        if (!estado(farmacia, usuario).abierta()) throw ApiException.conflict("CHAT_CLOSED", mensaje);
    }

    /**
     * Últimos mensajes en orden cronológico. Con despuesDe solo devuelve los posteriores a ese id
     * (lo usa el polling del dashboard y la app). "leido" indica si el otro lado ya lo leyó.
     */
    private List<MensajeDTO> mensajes(long farmacia, long usuario, Long despuesDe, String lector) {
        String sql = "SELECT * FROM (SELECT id_mensaje, autor, texto, fecha_hora, fecha_hora_lectura FROM mensaje_chat " +
            "WHERE id_farmacia = ? AND id_usuario = ? AND id_mensaje > ? ORDER BY id_mensaje DESC LIMIT ?) x " +
            "ORDER BY id_mensaje";
        return db.query(sql, (r, n) -> new MensajeDTO(r.getLong("id_mensaje"), r.getString("autor"),
            r.getString("texto"), fecha(r, "fecha_hora"), r.getTimestamp("fecha_hora_lectura") != null),
            farmacia, usuario, despuesDe == null ? 0L : despuesDe, MAX_MENSAJES);
    }

    private void marcarLeidos(long farmacia, long usuario, String autorDelOtroLado) {
        db.update("UPDATE mensaje_chat SET fecha_hora_lectura = ? WHERE id_farmacia = ? AND id_usuario = ? " +
            "AND autor = ? AND fecha_hora_lectura IS NULL", Timestamp.from(ahora()), farmacia, usuario, autorDelOtroLado);
    }

    private MensajeDTO insertar(long farmacia, long usuario, String autor, long cuenta, String texto) {
        Instant ahora = ahora();
        long id = db.queryForObject("INSERT INTO mensaje_chat (id_farmacia, id_usuario, autor, id_cuenta_autor, texto, " +
            "fecha_hora) VALUES (?, ?, ?, ?, ?, ?) RETURNING id_mensaje", Long.class,
            farmacia, usuario, autor, cuenta, texto, Timestamp.from(ahora));
        return new MensajeDTO(id, autor, texto, ApiClock.argentina(ahora), false);
    }

    private void operadorDe(long farmacia, CurrentAccount actor) {
        if (actor == null || !"OPERADOR".equals(actor.rol())) throw ApiException.forbidden();
        if (!Objects.equals(accounts.perfil(actor.id()).farmaciaId(), farmacia)) throw ApiException.forbidden();
    }

    private long usuarioDe(CurrentAccount actor) {
        if (actor == null || !"USUARIO".equals(actor.rol())) throw ApiException.forbidden();
        return db.query("SELECT id_usuario FROM usuario WHERE id_cuenta = ?", (r, n) -> r.getLong(1), actor.id())
            .stream().findFirst().orElseThrow(ApiException::forbidden);
    }

    /** 404 (y no 403) para no revelar qué pacientes tiene una farmacia ni viceversa. */
    private void conversacionPermitida(long farmacia, long usuario) {
        if (!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS (SELECT 1 FROM reserva WHERE id_farmacia = ? " +
            "AND id_usuario = ?)", Boolean.class, farmacia, usuario)))
            throw ApiException.notFound();
    }

    static String texto(EnviarMensajeRequest body) {
        String texto = body == null || body.texto() == null ? "" : body.texto().strip();
        if (texto.isEmpty()) throw ApiException.validation("El mensaje no puede estar vacío.");
        if (texto.codePointCount(0, texto.length()) > MAX_TEXTO)
            throw ApiException.validation("El mensaje no puede superar los " + MAX_TEXTO + " caracteres.");
        return texto;
    }

    private static String like(String raw) {
        return "%" + raw.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private Instant ahora() { return clock.instant().truncatedTo(ChronoUnit.MILLIS); }

    private static OffsetDateTime fecha(ResultSet r, String columna) throws SQLException {
        Timestamp t = r.getTimestamp(columna);
        return t == null ? null : ApiClock.argentina(t.toInstant());
    }
}
