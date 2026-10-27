package ar.edu.uade.medlink.reserva.service;

import ar.edu.uade.medlink.admin.ReservationClosureService;
import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.*;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.prescripcion.service.PrescriptionValidationService;
import ar.edu.uade.medlink.reserva.dto.*;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** RN-12..36, 6.3, 10.2/10.4 and 11.10. All mutations are single DB transactions.
 * The sole decision instant is captured before acquiring any locks.
 */
@Service
public class ReservaService {
    private final ReservaStore store;
    private final AccountRepository accounts;
    private final ReservationClosureService closure;
    private final PrescriptionValidationService prescriptions;
    private final AuditService audit;
    private final Clock clock;
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final char[] BASE32="0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final Set<String> OP_CAUSES=Set.of("STOCK_INCONSISTENTE","PRODUCTO_NO_DISPONIBLE",
        "PRODUCTO_NO_APTO_PARA_ENTREGA","IMPEDIMENTO_OPERATIVO_FARMACIA","ERROR_ADMINISTRATIVO","OTRA_CAUSA");

    public ReservaService(ReservaStore store, AccountRepository accounts, ReservationClosureService closure,
        PrescriptionValidationService prescriptions, AuditService audit, Clock clock) {
        this.store=store; this.accounts=accounts; this.closure=closure;
        this.prescriptions=prescriptions; this.audit=audit; this.clock=clock;
    }
    private Instant now() { return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }
    private static void requireRole(CurrentAccount a, String role) {
        if (a==null || !role.equals(a.rol())) throw ApiException.forbidden();
    }
    private long userId(CurrentAccount actor) {
        return store.db.query("SELECT id_usuario FROM usuario WHERE id_cuenta=?",
            (r,n)->r.getLong(1),actor.id()).stream().findFirst().orElseThrow(ApiException::forbidden);
    }
    private static String rxCode(String value) { return value==null?null:value.strip(); }
    private static String sha256(String value) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static String nextCode() {
        char[] chars=new char[12];
        for (int i=0;i<chars.length;i++) chars[i]=BASE32[RANDOM.nextInt(BASE32.length)];
        return new String(chars);
    }
    private static void positive(Long id) { if (id==null || id<=0) throw ApiException.validation("Identificador inválido."); }

    public record Creation(ReservaDTO dto, boolean suspended) { }
    @Transactional
    public Creation crear(CrearReservaRequest req, UUID key, CurrentAccount actor, HttpServletRequest request) {
        requireRole(actor,"USUARIO");
        if (key==null) throw ApiException.validation("Idempotency-Key UUID es obligatorio.");
        if (req==null) throw ApiException.validation("Datos de Reserva requeridos.");
        positive(req.farmaciaId()); positive(req.presentacionId());
        if (req.cantidad()==null || req.cantidad()<1) throw ApiException.validation("Cantidad inválida.");
        String code=rxCode(req.codigoPrescripcion());
        if (code!=null && code.length()>80) throw ApiException.validation("Código de prescripción inválido.");
        String hash=sha256(req.farmaciaId()+"|"+req.presentacionId()+"|"+req.cantidad()+"|"+(code==null?"":code));
        Instant t=now();
        store.lock("cuenta","id_cuenta",List.of(actor.id()));
        var account=accounts.byId(actor.id()).orElseThrow(ApiException::notFound);
        if (!"ACTIVA".equals(account.estado()))
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,"ACCOUNT_DISABLED","Cuenta deshabilitada.");
        var replay=store.db.query("SELECT request_hash,response_201::text FROM idempotency_record " +
            "WHERE id_cuenta=? AND endpoint='/api/reservas' AND idempotency_key=?",
            (r,n)->new String[]{r.getString(1),r.getString(2)},actor.id(),key);
        if (!replay.isEmpty()) {
            if (!replay.getFirst()[0].equals(hash))
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED","La clave ya se utilizó con otra solicitud.");
            return new Creation(JSON.readValue(replay.getFirst()[1],ReservaDTO.class),false);
        }
        long usuario=userId(actor);
        store.lock("usuario","id_usuario",List.of(usuario));
        closure.evaluateSuspensions(usuario,t,request);
        if (accounts.activeSuspension(usuario,t).isPresent())
            return new Creation(null,true);
        int active=store.db.queryForObject("SELECT count(*) FROM reserva WHERE id_usuario=? " +
            "AND estado_persistido='ACTIVA' AND fecha_hora_vencimiento>?",Integer.class,usuario,Timestamp.from(t));
        if (active>=5) throw ApiException.conflict("MAX_ACTIVE_RESERVATIONS_EXCEEDED","Límite de reservas activas alcanzado.");
        store.lock("farmacia","id_farmacia",List.of(req.farmaciaId()));
        String pharmacy=store.db.queryForObject("SELECT estado::text FROM farmacia WHERE id_farmacia=?",
            String.class,req.farmaciaId());
        if (!"ACTIVA".equals(pharmacy)) throw ApiException.conflict("PHARMACY_INACTIVE","La farmacia está inactiva.");
        if (!operatorExists(req.farmaciaId())) throw ApiException.conflict("PHARMACY_NOT_OPERATIONAL","La farmacia no tiene operadores activos.");
        long medication=store.db.query("SELECT id_medicamento FROM presentacion_medicamento WHERE id_presentacion=?",
            (r,n)->r.getLong(1),req.presentacionId()).stream().findFirst().orElseThrow(ApiException::notFound);
        store.lock("medicamento","id_medicamento",List.of(medication));
        store.lock("presentacion_medicamento","id_presentacion",List.of(req.presentacionId()));
        String medicineState=store.db.queryForObject("SELECT estado::text FROM medicamento WHERE id_medicamento=?",String.class,medication);
        String presentationState=store.db.queryForObject("SELECT estado::text FROM presentacion_medicamento WHERE id_presentacion=?",String.class,req.presentacionId());
        if (!"ACTIVO".equals(medicineState)) throw ApiException.conflict("MEDICATION_INACTIVE","Medicamento inactivo.");
        if (!"ACTIVA".equals(presentationState)) throw ApiException.conflict("PRESENTATION_INACTIVE","Presentación inactiva.");
        var product=store.product(req.farmaciaId(),req.presentacionId());
        if (product.condition().equals("VENTA_LIBRE")) {
            if (code!=null && !code.isEmpty()) throw ApiException.validation("VENTA_LIBRE no admite código de prescripción.");
            int otc=store.db.queryForObject("SELECT coalesce(sum(cantidad),0) FROM reserva " +
                "WHERE id_usuario=? AND id_presentacion=? AND estado_persistido='ACTIVA' AND fecha_hora_vencimiento>?",
                Integer.class,usuario,req.presentacionId(),Timestamp.from(t));
            if ((long)otc+req.cantidad()>3)
                throw ApiException.conflict("MAX_OTC_UNITS_EXCEEDED","Límite de unidades de venta libre superado.");
        } else if (code==null || code.isBlank()) throw ApiException.validation("La receta es obligatoria.");
        var stocks=store.db.query("SELECT id_stock,cantidad_disponible FROM stock " +
            "WHERE id_farmacia=? AND id_presentacion=? FOR UPDATE",
            (r,n)->new long[]{r.getLong(1),r.getLong(2)},req.farmaciaId(),req.presentacionId());
        if (stocks.isEmpty() || stocks.getFirst()[1]<req.cantidad())
            throw ApiException.conflict("INSUFFICIENT_STOCK","Stock insuficiente.");
        long stockId=stocks.getFirst()[0];
        int before=(int)stocks.getFirst()[1];
        Long item=null;
        if (product.condition().equals("BAJO_RECETA")) {
            // The replaceable Rx service locks Prescripción before Item and commits in this transaction.
            var valid=prescriptions.validarYComprometer(usuario,code,req.presentacionId(),req.cantidad(),t);
            item=valid.itemPrescripcionId();
        }
        String visible=null;
        Long reservation=null;
        for (int attempt=0;attempt<12 && reservation==null;attempt++) {
            String candidate=nextCode();
            var ids=store.db.query("""
                INSERT INTO reserva (codigo_visible,id_usuario,id_farmacia,id_presentacion,id_item_prescripcion,
                    cantidad,estado_persistido,fecha_hora_creacion,fecha_hora_vencimiento,
                    farmacia_nombre_snapshot,farmacia_direccion_snapshot,medicamento_nombre_snapshot,
                    presentacion_descripcion_snapshot)
                VALUES (?,?,?,?,?,?,'ACTIVA',?,?,?,?,?,?)
                ON CONFLICT (codigo_visible) DO NOTHING RETURNING id_reserva
                """,(r,n)->r.getLong(1),candidate,usuario,req.farmaciaId(),req.presentacionId(),item,
                req.cantidad(),Timestamp.from(t),Timestamp.from(t.plus(Duration.ofHours(4))),
                product.pharmacy(),product.address(),product.med(),product.presentation());
            if (!ids.isEmpty()) { visible=candidate; reservation=ids.getFirst(); }
        }
        if (reservation==null) throw new IllegalStateException("No fue posible generar un código aleatorio de Reserva.");
        int updated=store.db.update("UPDATE stock SET cantidad_disponible=?,fecha_hora_actualizacion=? " +
            "WHERE id_stock=? AND cantidad_disponible>=?",before-req.cantidad(),Timestamp.from(t),stockId,req.cantidad());
        if (updated!=1) throw ApiException.conflict("INSUFFICIENT_STOCK","Stock insuficiente.");
        movement(stockId,reservation,"RESERVA_CONFIRMADA",before,before-req.cantidad(),t,null,null);
        ReservaDTO response=store.get(visible,t,"USUARIO");
        store.db.update("INSERT INTO idempotency_record (id_cuenta,endpoint,idempotency_key,request_hash,response_201,fecha_hora_creacion) " +
            "VALUES (?,'/api/reservas',?, ?, ?::jsonb,?)",actor.id(),key,hash,JSON.writeValueAsString(response),Timestamp.from(t));
        return new Creation(response,false);
    }

    @Transactional(readOnly=true)
    public PageDTO<ReservaDTO> propias(int page,int size,String state,List<String> sort,CurrentAccount actor) {
        requireRole(actor,"USUARIO");
        long user=userId(actor);
        return store.page("r.id_usuario=?",new ArrayList<>(List.of(user)),state,null,null,page,size,sort,now(),"USUARIO");
    }
    @Transactional(readOnly=true)
    public ReservaDTO propia(String code,CurrentAccount actor) {
        requireRole(actor,"USUARIO");
        ReservaStore.Item item=store.item(code);
        if (item.usuario()!=userId(actor)) throw ApiException.notFound();
        return store.get(code,now(),"USUARIO");
    }
    @Transactional(readOnly=true)
    public PageDTO<ReservaDTO> deFarmacia(long pharmacy,String state,OffsetDateTime from,OffsetDateTime to,
        int page,int size,List<String> sort,CurrentAccount actor) {
        scope(pharmacy,actor);
        return store.page("r.id_farmacia=?",new ArrayList<>(List.of(pharmacy)),state,from,to,page,size,sort,now(),actor.rol());
    }
    @Transactional(readOnly=true)
    public ReservaDTO detalleFarmacia(long pharmacy,String code,CurrentAccount actor) {
        scope(pharmacy,actor);
        var item=store.item(code);
        if (item.farmacia()!=pharmacy) throw ApiException.notFound();
        return store.get(code,now(),actor.rol());
    }
    @Transactional(readOnly=true)
    public PageDTO<ReservaDTO> historialAdmin(long usuario,String state,OffsetDateTime from,OffsetDateTime to,
        int page,int size,List<String> sort,CurrentAccount actor) {
        requireRole(actor,"ADMINISTRADOR");
        existsUser(usuario);
        return store.page("r.id_usuario=?",new ArrayList<>(List.of(usuario)),state,from,to,page,size,sort,now(),actor.rol());
    }
    private void existsUser(long id) {
        if (store.db.queryForObject("SELECT count(*) FROM usuario WHERE id_usuario=?",Long.class,id)==0L) throw ApiException.notFound();
    }
    private void scope(long pharmacy,CurrentAccount actor) {
        if (actor==null || !(actor.rol().equals("OPERADOR")||actor.rol().equals("ADMINISTRADOR")))
            throw ApiException.forbidden();
        if (actor.rol().equals("OPERADOR") && !Objects.equals(accounts.perfil(actor.id()).farmaciaId(),pharmacy))
            throw ApiException.forbidden();
        if (store.db.queryForObject("SELECT count(*) FROM farmacia WHERE id_farmacia=?",Long.class,pharmacy)==0L)
            throw ApiException.notFound();
    }
    private boolean operatorExists(long pharmacy) {
        return Boolean.TRUE.equals(store.db.queryForObject("SELECT EXISTS (SELECT 1 FROM operador o JOIN cuenta c ON c.id_cuenta=o.id_cuenta " +
            "WHERE o.id_farmacia=? AND c.estado='ACTIVA')",Boolean.class,pharmacy));
    }
    private void activePharmacyForOperator(long pharmacy,CurrentAccount actor) {
        if (actor.rol().equals("OPERADOR")) {
            if (!"ACTIVA".equals(accounts.byId(actor.id()).orElseThrow(ApiException::forbidden).estado()))
                throw ApiException.forbidden();
            String state=store.db.queryForObject("SELECT estado::text FROM farmacia WHERE id_farmacia=?",String.class,pharmacy);
            if (!"ACTIVA".equals(state)) throw ApiException.conflict("PHARMACY_INACTIVE","Farmacia inactiva.");
        }
    }
    /** Never throw RESERVATION_EXPIRED *inside* this transaction; the controller throws AFTER commit. */
    public record Outcome(ReservaDTO dto,boolean expired) { }
    @Transactional
    public Outcome cancelarPropia(String code,CurrentAccount actor,HttpServletRequest request) {
        requireRole(actor,"USUARIO");
        Instant t=now();
        var target=store.item(code);
        if (target.usuario()!=userId(actor)) throw ApiException.notFound();
        var ctx=store.context(target);
        lockContext(ctx,target.id());
        var current=store.item(code);
        if (!current.estado().equals("ACTIVA")) throw invalidState();
        if (!current.vencimiento().isAfter(t)) {
            expire(current,ctx,t,request);
            return new Outcome(store.get(code,t,"USUARIO"),true);
        }
        cancel(current,ctx,t,"USUARIO","CANCELACION_USUARIO",null,true,null,actor,request);
        return new Outcome(store.get(code,t,"USUARIO"),false);
    }
    @Transactional
    public Outcome retirar(long pharmacy,String code,CurrentAccount actor,HttpServletRequest request) {
        requireRole(actor,"OPERADOR");
        scope(pharmacy,actor);
        Instant t=now();
        var target=store.item(code);
        if (target.farmacia()!=pharmacy) throw ApiException.notFound();
        var ctx=store.context(target);
        lockContext(ctx,target.id(),actor.id());
        activePharmacyForOperator(pharmacy,actor);
        var current=store.item(code);
        if (!current.estado().equals("ACTIVA")) throw invalidState();
        if (!current.vencimiento().isAfter(t)) {
            expire(current,ctx,t,request);
            return new Outcome(store.get(code,t,actor.rol()),true);
        }
        int updated=store.db.update("UPDATE reserva SET estado_persistido='COMPLETADA',fecha_hora_retiro=? " +
            "WHERE id_reserva=? AND estado_persistido='ACTIVA'",Timestamp.from(t),current.id());
        if (updated!=1) throw invalidState();
        if (ctx.item()!=null) {
            int row=store.db.update("UPDATE item_prescripcion SET cantidad_comprometida=cantidad_comprometida-?, " +
                "cantidad_utilizada=cantidad_utilizada+? WHERE id_item_prescripcion=? AND cantidad_comprometida>=?",
                current.cantidad(),current.cantidad(),ctx.item(),current.cantidad());
            if (row!=1) throw invalidState();
        }
        audit.record(actor.id(),actor.rol(),"CONFIRMAR_RETIRO","RESERVA",current.id(),current.usuario(),null,
            Map.of("estado","ACTIVA"),Map.of("estado","COMPLETADA"),request);
        return new Outcome(store.get(code,t,actor.rol()),false);
    }
    @Transactional
    public Outcome cancelarOperativa(long pharmacy,String code,CancelacionOperativaRequest cmd,
        CurrentAccount actor,HttpServletRequest request) {
        scope(pharmacy,actor);
        Instant t=now();
        var target=store.item(code);
        if (target.farmacia()!=pharmacy) throw ApiException.notFound();
        if (cmd==null || cmd.causa()==null) throw ApiException.validation("Causa requerida.");
        String cause=cmd.causa();
        if (cause.equals("CUENTA_DESHABILITADA") || cause.equals("CANCELACION_USUARIO") ||
            (cause.equals("ERROR_ADMINISTRATIVO") && !actor.rol().equals("ADMINISTRADOR")))
            throw ApiException.forbidden();
        if (!OP_CAUSES.contains(cause)) throw ApiException.validation("Causa inválida.");
        String reason=cmd.motivo()==null?null:cmd.motivo().strip();
        boolean mandatory=Set.of("PRODUCTO_NO_DISPONIBLE","PRODUCTO_NO_APTO_PARA_ENTREGA",
            "IMPEDIMENTO_OPERATIVO_FARMACIA","ERROR_ADMINISTRATIVO","OTRA_CAUSA").contains(cause);
        if (mandatory && (reason==null || reason.isEmpty() || reason.length()>500))
            throw ApiException.validation("Motivo obligatorio (1..500).");
        if (reason!=null && reason.length()>500) throw ApiException.validation("Motivo demasiado largo.");
        boolean choice=cause.equals("ERROR_ADMINISTRATIVO") || cause.equals("OTRA_CAUSA");
        // The REST contract uses explicit RESTITUIR / NO_RESTITUIR, not a boolean.
        String restoration=cmd.restitucionStock();
        if (choice!=(restoration!=null) ||
            (restoration!=null && !Set.of("RESTITUIR","NO_RESTITUIR").contains(restoration)) ||
            (cause.equals("STOCK_INCONSISTENTE")!=(cmd.cantidadStockReal()!=null)) ||
            (cmd.cantidadStockReal()!=null && cmd.cantidadStockReal()<0))
            throw ApiException.validation("Campos de cancelación incompatibles con causa.");
        boolean restore = switch(cause) {
            case "PRODUCTO_NO_DISPONIBLE","PRODUCTO_NO_APTO_PARA_ENTREGA","STOCK_INCONSISTENTE" -> false;
            case "IMPEDIMENTO_OPERATIVO_FARMACIA" -> true;
            default -> "RESTITUIR".equals(restoration);
        };
        var ctx=store.context(target);
        // STOCK_INCONSISTENTE first discovers ALL expired users of this pair to preserve 10.2 ordering.
        var pending=cause.equals("STOCK_INCONSISTENTE")?expiredPair(pharmacy,target.presentacion(),t):List.<ReservaStore.Item>of();
        var users=new TreeSet<Long>(); users.add(ctx.usuario());
        var accountsToLock=new TreeSet<Long>(); accountsToLock.add(ctx.cuenta());
        for (var p:pending) { var pc=store.context(p); users.add(pc.usuario()); accountsToLock.add(pc.cuenta()); }
        if (actor.rol().equals("OPERADOR")) accountsToLock.add(actor.id());
        store.lock("cuenta","id_cuenta",accountsToLock);
        store.lock("usuario","id_usuario",users);
        store.lock("farmacia","id_farmacia",List.of(pharmacy));
        activePharmacyForOperator(pharmacy,actor);
        store.lock("medicamento","id_medicamento",List.of(ctx.medicamento()));
        store.lock("presentacion_medicamento","id_presentacion",List.of(ctx.presentacion()));
        store.lock("stock","id_stock",List.of(ctx.stock()));
        var allRx=new TreeSet<Long>(); var allItems=new TreeSet<Long>();
        if (ctx.rx()!=null) allRx.add(ctx.rx()); if (ctx.item()!=null) allItems.add(ctx.item());
        for (var p:pending) { var pc=store.context(p); if(pc.rx()!=null) allRx.add(pc.rx()); if(pc.item()!=null) allItems.add(pc.item()); }
        store.lock("prescripcion","id_prescripcion",allRx);
        store.lock("item_prescripcion","id_item_prescripcion",allItems);
        var reservations=new TreeSet<Long>(); reservations.add(target.id()); pending.forEach(p->reservations.add(p.id()));
        store.lock("reserva","id_reserva",reservations);
        var current=store.item(code);
        if (!current.estado().equals("ACTIVA")) throw invalidState();
        if (!current.vencimiento().isAfter(t)) {
            expire(current,ctx,t,request);
            return new Outcome(store.get(code,t,actor.rol()),true);
        }
        if (cause.equals("STOCK_INCONSISTENTE")) {
            var actual=expiredPair(pharmacy,target.presentacion(),t);
            if (!new HashSet<>(pending.stream().map(ReservaStore.Item::id).toList())
                .containsAll(actual.stream().map(ReservaStore.Item::id).toList()))
                throw ApiException.conflict("INVALID_STATE_TRANSITION","Vencimientos concurrentes: reintentá.");
            for (var p:actual) expire(p,store.context(p),t,request);
        }
        cancel(current,ctx,t,actor.rol(),cause,reason,restore,cmd.cantidadStockReal(),actor,request);
        return new Outcome(store.get(code,t,actor.rol()),false);
    }
    private List<ReservaStore.Item> expiredPair(long pharmacy,long presentation,Instant t) {
        return store.db.query("SELECT codigo_visible FROM reserva WHERE id_farmacia=? AND id_presentacion=? " +
            "AND estado_persistido='ACTIVA' AND fecha_hora_vencimiento<=? ORDER BY id_reserva",
            (r,n)->r.getString(1),pharmacy,presentation,Timestamp.from(t)).stream().map(store::item).toList();
    }
    private static ApiException invalidState() {
        return ApiException.conflict("INVALID_STATE_TRANSITION","La Reserva ya no admite esa operación.");
    }
    private void movement(long stock,long reservation,String type,int prev,int next,Instant t,Long actor,String reason) {
        store.db.update("INSERT INTO movimiento_stock (id_stock,id_reserva,tipo,cantidad_anterior,cantidad_nueva,delta,fecha_hora,actor_cuenta_id,motivo) " +
            "VALUES (?,?,?::tipo_movimiento_stock,?,?,?,?,?,?)",stock,reservation,type,prev,next,next-prev,Timestamp.from(t),actor,reason);
    }
    private void adjustStock(ReservaStore.Context ctx,ReservaStore.Item item,int next,Instant t,String type,Long actor,String reason) {
        int old=store.db.queryForObject("SELECT cantidad_disponible FROM stock WHERE id_stock=?",Integer.class,ctx.stock());
        if (next<0) throw invalidState();
        store.db.update("UPDATE stock SET cantidad_disponible=?,fecha_hora_actualizacion=? WHERE id_stock=?",next,Timestamp.from(t),ctx.stock());
        movement(ctx.stock(),item.id(),type,old,next,t,actor,reason);
    }
    private void releaseRx(ReservaStore.Context ctx,int qty) {
        if (ctx.item()==null) return;
        int changed=store.db.update("UPDATE item_prescripcion SET cantidad_comprometida=cantidad_comprometida-? " +
            "WHERE id_item_prescripcion=? AND cantidad_comprometida>=?",qty,ctx.item(),qty);
        if (changed!=1) throw invalidState();
    }
    private void expire(ReservaStore.Item item,ReservaStore.Context ctx,Instant t,HttpServletRequest request) {
        if (!item.estado().equals("ACTIVA") || item.vencimiento().isAfter(t)) return;
        int updated=store.db.update("UPDATE reserva SET estado_persistido='VENCIDA' WHERE id_reserva=? " +
            "AND estado_persistido='ACTIVA'",item.id());
        if (updated!=1) throw invalidState();
        int stock=store.db.queryForObject("SELECT cantidad_disponible FROM stock WHERE id_stock=?",Integer.class,ctx.stock());
        if (stock>Integer.MAX_VALUE-item.cantidad()) throw invalidState();
        adjustStock(ctx,item,stock+item.cantidad(),t,"VENCIMIENTO",null,null);
        releaseRx(ctx,item.cantidad());
        closure.evaluateSuspensions(item.usuario(),t,request);
    }
    private void cancel(ReservaStore.Item item,ReservaStore.Context ctx,Instant t,String origin,String cause,
        String reason,boolean restore,Integer realStock,CurrentAccount actor,HttpServletRequest request) {
        int changed=store.db.update("UPDATE reserva SET estado_persistido='CANCELADA',fecha_hora_cancelacion=?," +
            "origen_cancelacion=?::origen_cancelacion,causa_cancelacion=?::causa_cancelacion,motivo_cancelacion=? " +
            "WHERE id_reserva=? AND estado_persistido='ACTIVA'",Timestamp.from(t),origin,cause,reason,item.id());
        if (changed!=1) throw invalidState();
        releaseRx(ctx,item.cantidad());
        int stock=store.db.queryForObject("SELECT cantidad_disponible FROM stock WHERE id_stock=?",Integer.class,ctx.stock());
        if (realStock!=null) {
            adjustStock(ctx,item,realStock,t,"CORRECCION_STOCK_INCONSISTENTE",actor.id(),reason);
            audit.record(actor.id(),actor.rol(),"CORREGIR_STOCK_INCONSISTENTE","STOCK",ctx.stock(),null,
                reason,Map.of("cantidadDisponible",stock),Map.of("cantidadDisponible",realStock),request);
        } else if (restore) {
            if (stock>Integer.MAX_VALUE-item.cantidad()) throw invalidState();
            adjustStock(ctx,item,stock+item.cantidad(),t,"CANCELACION_RESTITUCION",actor.id(),reason);
        }
        if (!origin.equals("USUARIO")) {
            audit.record(actor.id(),actor.rol(),"CANCELAR_RESERVA_OPERATIVA","RESERVA",item.id(),item.usuario(),reason,
                Map.of("estado","ACTIVA"),Map.of("estado","CANCELADA","causa",cause),request);
        }
    }
    /** Scheduler worker, one reservation per independent transaction. */
    @Transactional
    public void materializar(long reservationId,HttpServletRequest request) {
        Instant t=now();
        var initial=store.item(reservationId);
        if (!initial.estado().equals("ACTIVA")) return;
        if (initial.vencimiento().isAfter(t)) return;
        var ctx=store.context(initial);
        lockContext(ctx,initial.id());
        expire(store.item(reservationId),ctx,t,request);
    }
    private void lockContext(ReservaStore.Context ctx,long reservationId) {
        lockContext(ctx,reservationId,null);
    }
    private void lockContext(ReservaStore.Context ctx,long reservationId,Long actorAccountId) {
        var accountIds=new TreeSet<Long>(); accountIds.add(ctx.cuenta());
        if(actorAccountId!=null) accountIds.add(actorAccountId);
        store.lock("cuenta","id_cuenta",accountIds);
        store.lock("usuario","id_usuario",List.of(ctx.usuario()));
        store.lock("farmacia","id_farmacia",List.of(ctx.farmacia()));
        store.lock("medicamento","id_medicamento",List.of(ctx.medicamento()));
        store.lock("presentacion_medicamento","id_presentacion",List.of(ctx.presentacion()));
        store.lock("stock","id_stock",List.of(ctx.stock()));
        store.lock("prescripcion","id_prescripcion",ReservaStore.only(ctx.rx()));
        store.lock("item_prescripcion","id_item_prescripcion",ReservaStore.only(ctx.item()));
        store.lock("reserva","id_reserva",List.of(reservationId));
    }
}
