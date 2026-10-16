package ar.edu.uade.medlink.admin;

import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * RN-40: called INSIDE a transaction after locking Cuenta and Usuario.
 * Locks all relevant resources in 10.2 order, materializes expirations,
 * cancels functional actives, and reconciles the affected stock/Rx atomically.
 * The reservation module must use the same canonical locking contract.
 */
@Service
public class ReservationClosureService {
    private final JdbcTemplate db;
    private final AuditService audit;
    public ReservationClosureService(JdbcTemplate db, AuditService audit) { this.db=db; this.audit=audit; }

    record Reservation(long id, long pharmacy, long presentation, Long rxItem,
                       int quantity, Instant expires, long stockId) { }

    public void terminateUserReservations(long userId, Instant t, HttpServletRequest request) {
        List<Reservation> reservations = load(userId);
        if (reservations.isEmpty()) { evaluateSuspensions(userId,t,request); return; }

        // Canonical order: Farmacia -> Medicamento -> Presentación -> Stock
        // -> Prescripción -> ItemPrescripción -> Reserva; ascending ids per type.
        var pharmacies = new TreeSet<Long>(); var presentations=new TreeSet<Long>();
        var stocks=new TreeSet<Long>(); var items=new TreeSet<Long>();
        for (Reservation r:reservations) {
            pharmacies.add(r.pharmacy()); presentations.add(r.presentation()); stocks.add(r.stockId());
            if (r.rxItem()!=null) items.add(r.rxItem());
        }
        lockIds("farmacia","id_farmacia",pharmacies);
        var medicines = new TreeSet<Long>();
        for (long id : presentations) {
            medicines.add(db.queryForObject("SELECT id_medicamento FROM presentacion_medicamento WHERE id_presentacion=?", Long.class, id));
        }
        lockIds("medicamento","id_medicamento",medicines);
        lockIds("presentacion_medicamento","id_presentacion",presentations);
        lockIds("stock","id_stock",stocks);
        var prescriptions = new TreeSet<Long>();
        for (long id : items) {
            prescriptions.add(db.queryForObject("SELECT id_prescripcion FROM item_prescripcion WHERE id_item_prescripcion=?",Long.class,id));
        }
        lockIds("prescripcion","id_prescripcion",prescriptions);
        lockIds("item_prescripcion","id_item_prescripcion",items);
        var reservationIds=new TreeSet<Long>();
        reservations.forEach(r->reservationIds.add(r.id()));
        lockIds("reserva","id_reserva",reservationIds);

        // After locking, the state and relevant identities must still be valid.
        List<Reservation> actual = load(userId);
        if (actual.size() != reservations.size() || !sameIds(reservations,actual)) {
            throw ApiException.conflict("INVALID_STATE_TRANSITION", "Las reservas cambiaron durante la operación. Reintentá.");
        }
        for (Reservation r:actual) {
            if (!r.expires().isAfter(t)) {
                db.update("UPDATE reserva SET estado_persistido='VENCIDA' WHERE id_reserva=? AND estado_persistido='ACTIVA'",r.id());
                restore(r,"VENCIMIENTO",t);
            } else {
                db.update("UPDATE reserva SET estado_persistido='CANCELADA', fecha_hora_cancelacion=?, " +
                    "origen_cancelacion='SISTEMA', causa_cancelacion='CUENTA_DESHABILITADA' " +
                    "WHERE id_reserva=? AND estado_persistido='ACTIVA'",Timestamp.from(t),r.id());
                restore(r,"CANCELACION_RESTITUCION",t);
            }
            if (r.rxItem()!=null) {
                int rows=db.update("UPDATE item_prescripcion SET cantidad_comprometida=cantidad_comprometida-? " +
                    "WHERE id_item_prescripcion=? AND cantidad_comprometida>=?",r.quantity(),r.rxItem(),r.quantity());
                if (rows!=1) throw ApiException.conflict("INVALID_STATE_TRANSITION","Compromiso de receta inconsistente.");
            }
        }
        evaluateSuspensions(userId,t,request);
    }
    private static boolean sameIds(List<Reservation> a, List<Reservation> b) {
        if (a.size()!=b.size()) return false;
        var ids=new TreeSet<Long>();
        for (Reservation reservation:a) ids.add(reservation.id());
        for (Reservation reservation:b) if (!ids.remove(reservation.id())) return false;
        return ids.isEmpty();
    }
    private List<Reservation> load(long userId) {
        return db.query("SELECT r.id_reserva, r.id_farmacia, r.id_presentacion, " +
            "r.id_item_prescripcion,r.cantidad,r.fecha_hora_vencimiento,s.id_stock FROM reserva r " +
            "JOIN stock s ON s.id_farmacia=r.id_farmacia AND s.id_presentacion=r.id_presentacion " +
            "WHERE r.id_usuario=? AND r.estado_persistido='ACTIVA' ORDER BY r.id_reserva",
            (rs,n)->new Reservation(rs.getLong(1),rs.getLong(2),rs.getLong(3),
                (Long)rs.getObject(4),rs.getInt(5),rs.getObject(6,OffsetDateTime.class).toInstant(),rs.getLong(7)),userId);
    }
    private void lockIds(String table, String col, TreeSet<Long> ids) {
        // Identifiers are internal constants, never built from request input.
        if (ids.isEmpty()) return;
        String sql="SELECT "+col+" FROM "+table+" WHERE "+col+" IN ("+
            String.join(",",java.util.Collections.nCopies(ids.size(),"?"))+") ORDER BY "+col+" FOR UPDATE";
        List<Long> locked=db.queryForList(sql,Long.class,ids.toArray());
        if (locked.size()!=ids.size()) throw ApiException.conflict("INVALID_STATE_TRANSITION","Referencia inexistente al finalizar reservas.");
    }
    private void restore(Reservation r, String type, Instant t) {
        int previous = db.queryForObject("SELECT cantidad_disponible FROM stock WHERE id_stock=?",Integer.class,r.stockId());
        if (previous > Integer.MAX_VALUE-r.quantity()) throw ApiException.conflict("INVALID_STATE_TRANSITION","Saldo de stock excedido.");
        int next = previous + r.quantity();
        db.update("UPDATE stock SET cantidad_disponible=?,fecha_hora_actualizacion=? WHERE id_stock=?",next,Timestamp.from(t),r.stockId());
        db.update("INSERT INTO movimiento_stock (id_stock,id_reserva,tipo,cantidad_anterior,cantidad_nueva,delta,fecha_hora) " +
            "VALUES (?,?,?::tipo_movimiento_stock,?,?,?,?)",r.stockId(),r.id(),type,previous,next,r.quantity(),Timestamp.from(t));
    }

    // RN-31..36: materialize all historical intervals in one transaction.
    @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void evaluateSuspensions(long userId, Instant t, HttpServletRequest request) {
        var events = db.query("SELECT id_reserva, fecha_hora_vencimiento FROM reserva WHERE id_usuario=? " +
            "AND fecha_hora_vencimiento<=? AND (estado_persistido='VENCIDA' OR estado_persistido='ACTIVA') " +
            "ORDER BY fecha_hora_vencimiento ASC, id_reserva ASC",
            (rs,n)->new SuspensionPlanner.Event(rs.getLong(1),
                rs.getObject(2,OffsetDateTime.class).toInstant()),userId,Timestamp.from(t));
        var existing = db.query("SELECT fecha_hora_fin FROM suspension WHERE id_usuario=? " +
            "ORDER BY fecha_hora_fin DESC, id_suspension DESC LIMIT 1",
            (rs,n)->rs.getObject(1,OffsetDateTime.class).toInstant(),userId);
        Instant cursor=existing.isEmpty()?null:existing.get(0);
        for (SuspensionPlanner.Interval interval : SuspensionPlanner.plan(events,cursor)) {
            long id=db.queryForObject("INSERT INTO suspension (id_usuario,fecha_hora_inicio,fecha_hora_fin,motivo) " +
                "VALUES (?,?,?,'INCUMPLIMIENTOS_REITERADOS') RETURNING id_suspension",Long.class,
                userId,Timestamp.from(interval.from()),Timestamp.from(interval.to()));
            audit.record(null,"SISTEMA","CREAR_SUSPENSION","SUSPENSION",id,userId,
                null,null,Map.of("fechaHoraInicio",ApiClock.argentina(interval.from()).toString(),
                    "fechaHoraFin",ApiClock.argentina(interval.to()).toString()),request);
        }
    }
}
