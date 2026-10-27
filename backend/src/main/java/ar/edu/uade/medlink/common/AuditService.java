package ar.edu.uade.medlink.common;

import ar.edu.uade.medlink.auditoria.AuditoriaDTO;
import ar.edu.uade.medlink.auditoria.AuditoriaFiltro;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AuditService {
    private final JdbcTemplate db;
    private final Clock clock;
    public AuditService(JdbcTemplate db, Clock clock) { this.db = db; this.clock = clock; }

    private static final JsonMapper JSON=JsonMapper.builder().build();
    // Scalar operational fields used by existing modules. Unknown fields (including
    // credentials and nested objects) are never persisted or returned from legacy rows.
    private static final Set<String> SUMMARY_FIELDS=Set.of("nombre","apellido","cuentaId","farmaciaId",
        "estado","tokenVersion","direccion","telefono","emailContacto","medicamentoId",
        "concentracionTexto","formaFarmaceutica","cantidadEnvase","unidadExpendio","condicionDispensa",
        "presentacionId","cantidadDisponible","fechaHoraInicio","fechaHoraFin",
        "id_farmacia","cantidad_disponible","causa",
        "codigoExterno","usuarioId","fechaEmision","fechaVencimiento","cantidadItems"); // Existing seed summaries use these physical names.

    // Only whitelisted public operational fields may be added to audit summaries.
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(Long actorId, String actorRole, String action, String entityType,
                       long entityId, Long userId, String reason,
                       Map<String, ?> before, Map<String, ?> after, HttpServletRequest request) {
        Object id = request == null ? null : request.getAttribute("correlationId");
        UUID correlation = null;
        if (id instanceof String value) {
            try { correlation=UUID.fromString(value); }
            catch (IllegalArgumentException ignored) { /* Non-HTTP callers may omit correlation. */ }
        }
        if (after==null) throw new IllegalArgumentException("resumenPosterior es obligatorio.");
        db.update("INSERT INTO auditoria (fecha_hora, actor_cuenta_id, actor_rol, accion, tipo_entidad," +
            " entidad_id, usuario_relacionado_id, motivo, correlation_id, resumen_anterior, resumen_posterior) " +
            "VALUES (?, ?, ?::actor_sistema, ?::accion_auditoria, ?::tipo_entidad_auditoria, ?, ?, ?, ?, ?::jsonb, ?::jsonb)",
            Timestamp.from(clock.instant()), actorId, actorRole, action, entityType, entityId,
            userId, reason, correlation, before == null ? null : json(before), json(after));
    }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageDTO<AuditoriaDTO> listar(AuditoriaFiltro filter, int page, int size,
                                       List<String> sort, CurrentAccount actor) {
        if (actor==null || !"ADMINISTRADOR".equals(actor.rol())) throw ApiException.forbidden();
        var pageable=PageRequests.of(page,size,sort,Set.of("fechaHora","id"),
            Sort.by(Sort.Order.desc("fechaHora"),Sort.Order.desc("id")));
        StringBuilder where=new StringBuilder(" WHERE 1=1");
        List<Object> values=new ArrayList<>();
        equal(where,values,"a.usuario_relacionado_id",filter.usuarioId());
        equal(where,values,"a.actor_cuenta_id",filter.actorCuentaId());
        equal(where,values,"a.actor_rol",filter.rol(),"::actor_sistema");
        equal(where,values,"a.accion",filter.accion(),"::accion_auditoria");
        equal(where,values,"a.tipo_entidad",filter.tipoEntidad(),"::tipo_entidad_auditoria");
        equal(where,values,"a.entidad_id",filter.entidadId());
        if (filter.desde()!=null) { where.append(" AND a.fecha_hora>=?"); values.add(filter.desde()); }
        if (filter.hasta()!=null) { where.append(" AND a.fecha_hora<?"); values.add(filter.hasta()); }
        long total=db.queryForObject("SELECT count(*) FROM auditoria a"+where,Long.class,values.toArray());
        Map<String,String> columns=Map.of("id","a.id_auditoria","fechaHora","a.fecha_hora");
        String order=String.join(",",pageable.getSort().stream()
            .map(o->columns.get(o.getProperty())+" "+o.getDirection().name()).toList());
        values.add(size); values.add(pageable.getOffset());
        var rows=db.query("""
            SELECT a.*, COALESCE(ad.nombre||' '||ad.apellido, o.nombre||' '||o.apellido,
                u.nombre||' '||u.apellido) AS actor_nombre
            FROM auditoria a
            LEFT JOIN administrador ad ON ad.id_cuenta=a.actor_cuenta_id
            LEFT JOIN operador o ON o.id_cuenta=a.actor_cuenta_id
            LEFT JOIN usuario u ON u.id_cuenta=a.actor_cuenta_id
            """+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",(r,n)->new AuditoriaDTO(
                r.getLong("id_auditoria"),ApiClock.timestamp(r.getObject("fecha_hora",OffsetDateTime.class).toInstant()),
                r.getObject("actor_cuenta_id",Long.class),r.getString("actor_nombre"),r.getString("actor_rol"),
                r.getString("accion"),r.getString("tipo_entidad"),r.getLong("entidad_id"),
                r.getObject("usuario_relacionado_id",Long.class),r.getString("motivo"),
                r.getString("correlation_id"),summary(r.getString("resumen_anterior")),
                summary(r.getString("resumen_posterior"))),values.toArray());
        return PageDTO.from(rows,page,size,total);
    }

    private static void equal(StringBuilder where,List<Object> args,String column,Object value) {
        equal(where,args,column,value,"");
    }
    private static void equal(StringBuilder where,List<Object> args,String column,Object value,String cast) {
        if (value!=null) { where.append(" AND ").append(column).append("=?").append(cast); args.add(value); }
    }
    private static Map<String,Object> safe(Map<String,?> values) {
        Map<String,Object> result=new LinkedHashMap<>();
        values.forEach((key,value)-> {
            if (SUMMARY_FIELDS.contains(key) && (value==null || value instanceof String
                || value instanceof Number || value instanceof Boolean)) result.put(key,value);
        });
        return Collections.unmodifiableMap(result);
    }
    private static Map<String,Object> summary(String json) {
        if (json==null) return null;
        return safe(JSON.readValue(json,new TypeReference<Map<String,Object>>() { }));
    }
    private static String json(Map<String, ?> values) {
        StringBuilder builder = new StringBuilder("{");
        for (var entry: safe(values).entrySet()) {
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
