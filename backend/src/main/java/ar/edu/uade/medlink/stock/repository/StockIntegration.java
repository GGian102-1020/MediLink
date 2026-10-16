package ar.edu.uade.medlink.stock.repository;

import ar.edu.uade.medlink.admin.ReservationClosureService;
import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import ar.edu.uade.medlink.stock.dto.DisponibilidadFarmaciaDTO;
import ar.edu.uade.medlink.stock.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;

/** Transactional bridge to the existing catalog, accounts, prescriptions and reservations.
 * No parallel domain entities; all writes join StockService's JPA transaction and datasource.
 */
@Repository
public class StockIntegration {
    private final EntityManager em;
    private final AccountRepository accounts;
    private final ReservationClosureService closure;
    private final MovimientoStockRepository movimientos;
    public StockIntegration(EntityManager em, AccountRepository accounts,
                            ReservationClosureService closure, MovimientoStockRepository movimientos) {
        this.em=em; this.accounts=accounts; this.closure=closure; this.movimientos=movimientos;
    }
    public void autorizar(long farmaciaId, CurrentAccount actor) {
        if (actor==null) throw ApiException.forbidden();
        if ("ADMINISTRADOR".equals(actor.rol())) return;
        if (!"OPERADOR".equals(actor.rol())) throw ApiException.forbidden();
        var perfil=accounts.perfil(actor.id());
        if (!"ACTIVA".equals(perfil.estadoCuenta()) || !Objects.equals(perfil.farmaciaId(),farmaciaId))
            throw ApiException.forbidden();
    }
    public void bloquearCatalogoActivo(long presentacionId) {
        var ids=em.createNativeQuery("SELECT id_medicamento FROM presentacion_medicamento WHERE id_presentacion=:id")
            .setParameter("id",presentacionId).getResultList();
        if (ids.isEmpty()) throw ApiException.notFound();
        long medicine=((Number)ids.getFirst()).longValue();
        String med=(String)em.createNativeQuery("SELECT estado::text FROM medicamento WHERE id_medicamento=:id FOR UPDATE")
            .setParameter("id",medicine).getSingleResult();
        String presentation=(String)em.createNativeQuery("SELECT estado::text FROM presentacion_medicamento WHERE id_presentacion=:id FOR UPDATE")
            .setParameter("id",presentacionId).getSingleResult();
        if (!"ACTIVO".equals(med)) throw ApiException.conflict("MEDICATION_INACTIVE","El medicamento está inactivo.");
        if (!"ACTIVA".equals(presentation)) throw ApiException.conflict("PRESENTATION_INACTIVE","La presentación está inactiva.");
    }

    public record Pendiente(long id, long usuarioId, long cuentaId, Long itemId, Long prescripcionId, int cantidad) { }

    /** Discovery is read-only; locks on Cuenta Usuario and Usuario precede Farmacia. */
    public List<Pendiente> bloquearUsuariosVencidos(long farmacia, long presentacion, Instant t) {
        List<Pendiente> pending=pendientes(farmacia,presentacion,t);
        lockIds("cuenta","id_cuenta",sorted(pending.stream().map(Pendiente::cuentaId).toList()));
        lockIds("usuario","id_usuario",sorted(pending.stream().map(Pendiente::usuarioId).toList()));
        return pending;
    }
    public void materializarVencimientos(long farmacia, long presentacion, Instant t, Stock stock,
                                         List<Pendiente> discovered, HttpServletRequest request) {
        List<Pendiente> actual=pendientes(farmacia,presentacion,t);
        // Newly discovered references require restarting from the beginning of the locking order.
        if (!new HashSet<>(discovered).containsAll(actual))
            throw ApiException.conflict("INVALID_STATE_TRANSITION","Cambiaron los vencimientos pendientes. Reintentá.");
        if (actual.isEmpty()) return;
        if (stock==null) throw ApiException.conflict("INVALID_STATE_TRANSITION","Hay reservas sin registro de stock.");
        lockIds("prescripcion","id_prescripcion",sorted(actual.stream().map(Pendiente::prescripcionId).filter(Objects::nonNull).toList()));
        lockIds("item_prescripcion","id_item_prescripcion",sorted(actual.stream().map(Pendiente::itemId).filter(Objects::nonNull).toList()));
        lockIds("reserva","id_reserva",sorted(actual.stream().map(Pendiente::id).toList()));
        // Re-read after all locks. Terminal reservations must never restitute twice.
        actual=pendientes(farmacia,presentacion,t);
        for (Pendiente pending:actual) {
            int before=stock.getCantidadDisponible();
            if (before>Integer.MAX_VALUE-pending.cantidad())
                throw ApiException.conflict("INVALID_STATE_TRANSITION","Saldo de stock excedido.");
            int changed=em.createNativeQuery("UPDATE reserva SET estado_persistido='VENCIDA' WHERE id_reserva=:id AND estado_persistido='ACTIVA'")
                .setParameter("id",pending.id()).executeUpdate();
            if (changed!=1) throw ApiException.conflict("INVALID_STATE_TRANSITION","La reserva cambió durante el vencimiento.");
            if (pending.itemId()!=null) {
                int rows=em.createNativeQuery("""
                    UPDATE item_prescripcion SET cantidad_comprometida=cantidad_comprometida-:cantidad
                    WHERE id_item_prescripcion=:id AND cantidad_comprometida>=:cantidad
                    """).setParameter("cantidad",pending.cantidad()).setParameter("id",pending.itemId()).executeUpdate();
                if (rows!=1) throw ApiException.conflict("INVALID_STATE_TRANSITION","Compromiso de receta inconsistente.");
            }
            stock.establecerCantidad(before+pending.cantidad(),t);
            movimientos.saveAndFlush(new MovimientoStock(stock,pending.id(),TipoMovimientoStock.VENCIMIENTO,
                before,stock.getCantidadDisponible(),t,null,null));
        }
        em.flush();
        for (long user:sorted(actual.stream().map(Pendiente::usuarioId).toList())) {
            // Reuses the team's deterministic RN-31..36 implementation under the Usuario lock.
            closure.evaluateSuspensions(user,t,request);
        }
    }
    @SuppressWarnings("unchecked")
    private List<Pendiente> pendientes(long farmacia, long presentacion, Instant t) {
        List<Object[]> rows=em.createNativeQuery("""
            SELECT r.id_reserva,r.id_usuario,u.id_cuenta,r.id_item_prescripcion,i.id_prescripcion,r.cantidad
            FROM reserva r JOIN usuario u ON u.id_usuario=r.id_usuario
            LEFT JOIN item_prescripcion i ON i.id_item_prescripcion=r.id_item_prescripcion
            WHERE r.id_farmacia=:farmacia AND r.id_presentacion=:presentacion
            AND r.estado_persistido='ACTIVA' AND r.fecha_hora_vencimiento<=:t ORDER BY r.id_reserva
            """).setParameter("farmacia",farmacia).setParameter("presentacion",presentacion).setParameter("t",t).getResultList();
        return rows.stream().map(r->new Pendiente(number(r[0]),number(r[1]),number(r[2]),
            r[3]==null?null:number(r[3]),r[4]==null?null:number(r[4]),((Number)r[5]).intValue())).toList();
    }
    private static long number(Object value) { return ((Number)value).longValue(); }
    private static TreeSet<Long> sorted(List<Long> ids) { return new TreeSet<>(ids); }
    private void lockIds(String table, String column, TreeSet<Long> ids) {
        if (ids.isEmpty()) return;
        // Table and column names are internal constants, never request input.
        var rows=em.createNativeQuery("SELECT "+column+" FROM "+table+" WHERE "+column+" IN (:ids) ORDER BY "+column+" FOR UPDATE")
            .setParameter("ids",ids).getResultList();
        if (rows.size()!=ids.size()) throw ApiException.conflict("INVALID_STATE_TRANSITION","Referencia inexistente al vencer reservas.");
    }
    @SuppressWarnings("unchecked")
    public Map<Long,String> codigosReservas(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<Object[]> rows=em.createNativeQuery("SELECT id_reserva,codigo_visible FROM reserva WHERE id_reserva IN (:ids)")
            .setParameter("ids",ids).getResultList();
        Map<Long,String> codes=new HashMap<>();
        for (Object[] r:rows) codes.put(number(r[0]),(String)r[1]);
        return codes;
    }
    @SuppressWarnings("unchecked")
    public List<DisponibilidadFarmaciaDTO> disponibilidad(long presentacion) {
        if (em.createNativeQuery("SELECT id_presentacion FROM presentacion_medicamento WHERE id_presentacion=:id")
            .setParameter("id",presentacion).getResultList().isEmpty()) throw ApiException.notFound();
        List<Object[]> rows=em.createNativeQuery("""
            SELECT f.id_farmacia,f.nombre,f.direccion,s.cantidad_disponible FROM stock s
            JOIN farmacia f ON f.id_farmacia=s.id_farmacia
            JOIN presentacion_medicamento p ON p.id_presentacion=s.id_presentacion
            JOIN medicamento m ON m.id_medicamento=p.id_medicamento
            WHERE s.id_presentacion=:id AND s.cantidad_disponible>0 AND f.estado='ACTIVA'
            AND p.estado='ACTIVA' AND m.estado='ACTIVO'
            AND EXISTS (SELECT 1 FROM operador o JOIN cuenta c ON c.id_cuenta=o.id_cuenta
                        WHERE o.id_farmacia=f.id_farmacia AND c.estado='ACTIVA')
            ORDER BY f.nombre ASC,f.id_farmacia ASC
            """).setParameter("id",presentacion).getResultList();
        return rows.stream().map(r->new DisponibilidadFarmaciaDTO(number(r[0]),(String)r[1],(String)r[2],((Number)r[3]).intValue())).toList();
    }
}
