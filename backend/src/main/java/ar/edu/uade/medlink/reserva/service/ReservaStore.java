package ar.edu.uade.medlink.reserva.service;

import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.common.PageRequests;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.reserva.dto.ReservaDTO;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC read side uses the persisted schema and effective-state rule CV-10. */
@Repository
public class ReservaStore {
    final JdbcTemplate db;
    public ReservaStore(JdbcTemplate db) { this.db=db; }

    static final String EFFECTIVE = "CASE WHEN r.estado_persistido='ACTIVA' AND r.fecha_hora_vencimiento<=? " +
        "THEN 'VENCIDA' ELSE r.estado_persistido::text END";
    private static final String FIELDS = """
        SELECT r.*, p.condicion_dispensa::text AS condicion, u.nombre AS usuario_nombre,
        u.apellido AS usuario_apellido, rx.codigo_externo AS codigo_rx,
        i.id_prescripcion AS id_prescripcion
        FROM reserva r JOIN usuario u ON u.id_usuario=r.id_usuario
        JOIN presentacion_medicamento p ON p.id_presentacion=r.id_presentacion
        LEFT JOIN item_prescripcion i ON i.id_item_prescripcion=r.id_item_prescripcion
        LEFT JOIN prescripcion rx ON rx.id_prescripcion=i.id_prescripcion
        """;

    record Item(long id, long usuario, long farmacia, long presentacion, Long rxItem, Long rx,
                int cantidad, String estado, Instant vencimiento) { }
    record Context(long usuario, long cuenta, long farmacia, long medicamento, long presentacion,
                   long stock, Long rx, Long item) { }
    record Product(String pharmacy, String address, String med, String presentation, String condition) { }

    Item item(String codigo) {
        return db.query("SELECT r.id_reserva,r.id_usuario,r.id_farmacia,r.id_presentacion,r.id_item_prescripcion, " +
            "i.id_prescripcion,r.cantidad,r.estado_persistido::text,r.fecha_hora_vencimiento " +
            "FROM reserva r LEFT JOIN item_prescripcion i ON i.id_item_prescripcion=r.id_item_prescripcion " +
            "WHERE r.codigo_visible=upper(?)", (rs,n)->new Item(rs.getLong(1),rs.getLong(2),rs.getLong(3),
                rs.getLong(4), (Long)rs.getObject(5),(Long)rs.getObject(6),rs.getInt(7),rs.getString(8),
                rs.getObject(9,OffsetDateTime.class).toInstant()), codigo).stream().findFirst().orElseThrow(ApiException::notFound);
    }
    Item item(long id) {
        return db.query("SELECT codigo_visible FROM reserva WHERE id_reserva=?", (rs,n)->rs.getString(1),id)
            .stream().map(this::item).findFirst().orElseThrow(ApiException::notFound);
    }
    Context context(Item r) {
        return db.query("""
            SELECT r.id_usuario,u.id_cuenta,r.id_farmacia,p.id_medicamento,r.id_presentacion,
                   s.id_stock,i.id_prescripcion,r.id_item_prescripcion
            FROM reserva r JOIN usuario u ON u.id_usuario=r.id_usuario
            JOIN presentacion_medicamento p ON p.id_presentacion=r.id_presentacion
            JOIN stock s ON s.id_farmacia=r.id_farmacia AND s.id_presentacion=r.id_presentacion
            LEFT JOIN item_prescripcion i ON i.id_item_prescripcion=r.id_item_prescripcion
            WHERE r.id_reserva=?
            """, (rs,n)->new Context(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),
                rs.getLong(5),rs.getLong(6),(Long)rs.getObject(7),(Long)rs.getObject(8)),r.id())
            .stream().findFirst().orElseThrow(ApiException::notFound);
    }
    void lock(String table, String col, Collection<Long> ids) {
        if (ids.isEmpty()) return;
        var unique=new TreeSet<>(ids);
        String placeholders=String.join(",",Collections.nCopies(unique.size(),"?"));
        var got=db.queryForList("SELECT "+col+" FROM "+table+" WHERE "+col+" IN ("+placeholders+") ORDER BY "+col+" FOR UPDATE",
            Long.class,unique.toArray());
        if (got.size()!=unique.size()) throw ApiException.notFound();
    }
    static Collection<Long> only(Long id) { return id==null?List.of():List.of(id); }
    Product product(long farmacia, long presentacion) {
        return db.query("""
            SELECT f.nombre,f.direccion,m.nombre,p.concentracion_texto,p.forma_farmaceutica,
                p.cantidad_envase,p.unidad_expendio,p.condicion_dispensa::text
            FROM farmacia f CROSS JOIN presentacion_medicamento p
            JOIN medicamento m ON m.id_medicamento=p.id_medicamento
            WHERE f.id_farmacia=? AND p.id_presentacion=?
            """,(rs,n)->new Product(rs.getString(1),rs.getString(2),rs.getString(3),
                rs.getString(4)+" "+rs.getString(5)+" "+rs.getInt(6)+" "+rs.getString(7),rs.getString(8)),
            farmacia,presentacion).stream().findFirst().orElseThrow(ApiException::notFound);
    }
    ReservaDTO get(String code, Instant t, String view) {
        return db.query(FIELDS+" WHERE r.codigo_visible=upper(?)",(rs,n)->map(rs,t,view),code)
            .stream().findFirst().orElseThrow(ApiException::notFound);
    }
    ReservaDTO get(long id, Instant t, String view) {
        return db.query(FIELDS+" WHERE r.id_reserva=?",(rs,n)->map(rs,t,view),id)
            .stream().findFirst().orElseThrow(ApiException::notFound);
    }
    private static String time(ResultSet r,String col) throws SQLException {
        OffsetDateTime dt=r.getObject(col,OffsetDateTime.class);
        return dt==null?null:ApiClock.timestamp(dt.toInstant());
    }
    private static ReservaDTO map(ResultSet r, Instant t, String view) throws SQLException {
        Instant exp=r.getObject("fecha_hora_vencimiento",OffsetDateTime.class).toInstant();
        String status=r.getString("estado_persistido");
        if (status.equals("ACTIVA") && !exp.isAfter(t)) status="VENCIDA";
        long seconds=status.equals("ACTIVA")?Math.max(0,java.time.Duration.between(t,exp).toSeconds()):0;
        return new ReservaDTO(r.getLong("id_reserva"),r.getString("codigo_visible"),status,r.getInt("cantidad"),
            time(r,"fecha_hora_creacion"),time(r,"fecha_hora_vencimiento"),seconds,
            time(r,"fecha_hora_cancelacion"),time(r,"fecha_hora_retiro"),r.getLong("id_farmacia"),
            r.getString("farmacia_nombre_snapshot"),r.getString("farmacia_direccion_snapshot"),
            r.getLong("id_presentacion"),r.getString("medicamento_nombre_snapshot"),
            r.getString("presentacion_descripcion_snapshot"),r.getString("condicion"),
            !view.equals("USUARIO")?r.getString("usuario_nombre"):null,!view.equals("USUARIO")?r.getString("usuario_apellido"):null,
            r.getString("origen_cancelacion"),r.getString("causa_cancelacion"),r.getString("motivo_cancelacion"),
            view.equals("OPERADOR")?null:r.getString("codigo_rx"));
    }
    PageDTO<ReservaDTO> page(String where, List<Object> args, String state, OffsetDateTime from,
        OffsetDateTime to, int page, int size, List<String> sort, Instant t, String view) {
        var pagination=PageRequests.of(page,size,sort,Set.of("fechaHoraCreacion","fechaHoraVencimiento","estado","id"),
            Sort.by(Sort.Order.desc("fechaHoraCreacion"),Sort.Order.desc("id")));
        if (state!=null) {
            if (!Set.of("ACTIVA","COMPLETADA","CANCELADA","VENCIDA").contains(state))
                throw ApiException.validation("Estado de Reserva inválido.");
            where+=" AND "+EFFECTIVE+"=?";
            args.add(Timestamp.from(t)); args.add(state);
        }
        if (from!=null) { where+=" AND r.fecha_hora_creacion>=?"; args.add(Timestamp.from(from.toInstant())); }
        if (to!=null) { where+=" AND r.fecha_hora_creacion<?"; args.add(Timestamp.from(to.toInstant())); }
        if (from!=null && to!=null && !from.isBefore(to)) throw ApiException.validation("El rango de fechas es inválido.");
        long total=db.queryForObject("SELECT count(*) FROM reserva r WHERE "+where,Long.class,args.toArray());
        Map<String,String> columns=Map.of("id","r.id_reserva","fechaHoraCreacion","r.fecha_hora_creacion",
            "fechaHoraVencimiento","r.fecha_hora_vencimiento","estado",EFFECTIVE);
        List<Object> ordered=new ArrayList<>(args);
        StringJoiner order=new StringJoiner(",");
        for (var o:pagination.getSort()) {
            order.add(columns.get(o.getProperty())+" "+o.getDirection().name());
            if (o.getProperty().equals("estado")) ordered.add(Timestamp.from(t));
        }
        // For SELECT, WHERE bindings appear before ORDER BY bindings.
        ordered.add(size); ordered.add(pagination.getOffset());
        return PageDTO.from(db.query(FIELDS+" WHERE "+where+" ORDER BY "+order+" LIMIT ? OFFSET ?",
            (rs,n)->map(rs,t,view),ordered.toArray()),page,size,total);
    }
}
