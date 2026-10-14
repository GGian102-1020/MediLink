package ar.edu.uade.medlink.common;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcTemplate db;
    private final Clock clock;
    public AuditService(JdbcTemplate db, Clock clock) { this.db = db; this.clock = clock; }

    // Only whitelisted public operational fields may be added to audit summaries.
    public void record(Long actorId, String actorRole, String action, String entityType,
                       long entityId, Long userId, String reason,
                       Map<String, ?> before, Map<String, ?> after, HttpServletRequest request) {
        Object id = request == null ? null : request.getAttribute("correlationId");
        UUID correlation = id instanceof String ? UUID.fromString((String) id) : null;
        db.update("INSERT INTO auditoria (fecha_hora, actor_cuenta_id, actor_rol, accion, tipo_entidad," +
            " entidad_id, usuario_relacionado_id, motivo, correlation_id, resumen_anterior, resumen_posterior) " +
            "VALUES (?, ?, ?::actor_sistema, ?::accion_auditoria, ?::tipo_entidad_auditoria, ?, ?, ?, ?, ?::jsonb, ?::jsonb)",
            Timestamp.from(clock.instant()), actorId, actorRole, action, entityType, entityId,
            userId, reason, correlation, before == null ? null : json(before), json(after));
    }

    private static String json(Map<String, ?> values) {
        StringBuilder builder = new StringBuilder("{");
        for (var entry: values.entrySet()) {
            if (builder.length() > 1) builder.append(',');
            builder.append('"').append(escape(entry.getKey())).append("\":");
            Object v = entry.getValue();
            if (v == null) builder.append("null");
            else if (v instanceof Number || v instanceof Boolean) builder.append(v);
            else builder.append('"').append(escape(v.toString())).append('"');
        }
        return builder.append('}').toString();
    }
    private static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            switch(ch) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> { if (ch < 32) b.append(String.format("\\u%04x", (int) ch)); else b.append(ch); }
            }
        }
        return b.toString();
    }
}
