package ar.edu.uade.medlink.reserva;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.reserva.dto.*;
import ar.edu.uade.medlink.reserva.service.ReservaService;
import ar.edu.uade.medlink.security.CurrentAccount;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Real PG16 and Flyway. Tests the DB triggers, locks and rollback semantics (AC-01..60). */
@SpringBootTest(properties={"medlink.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.flyway.baseline-on-migrate=false", "medlink.reservas.scheduler.enabled=false"})
@Import(ReservasPostgresTest.FixedTime.class)
@EnabledIf("postgresDisponible")
class ReservasPostgresTest {
    static final Instant T=Instant.parse("2026-10-27T16:00:00.000Z");
    static PostgreSQLContainer container;
    static boolean postgresDisponible() {
        return System.getenv("MEDLINK_TEST_DB_URL")!=null || DockerClientFactory.instance().isDockerAvailable();
    }
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) {
        String url=System.getenv("MEDLINK_TEST_DB_URL");
        if (url==null) {
            container=new PostgreSQLContainer("postgres:16-alpine"); container.start();
            registry.add("spring.datasource.url",container::getJdbcUrl);
            registry.add("spring.datasource.username",container::getUsername);
            registry.add("spring.datasource.password",container::getPassword);
        } else {
            registry.add("spring.datasource.url",()->url);
            registry.add("spring.datasource.username",()->System.getenv().getOrDefault("MEDLINK_TEST_DB_USER","postgres"));
            registry.add("spring.datasource.password",()->System.getenv().getOrDefault("MEDLINK_TEST_DB_PASSWORD",""));
        }
    }
    @AfterAll static void stop() { if (container!=null) container.stop(); }
    @TestConfiguration static class FixedTime {
        @Bean @Primary Clock clockForReservations() { return Clock.fixed(T,ZoneOffset.UTC); }
    }
    @Autowired JdbcTemplate db;
    @Autowired AccountRepository accounts;
    @Autowired ReservaService reservations;
    @Autowired PlatformTransactionManager transactions;

    record Fixture(long pharmacy,long medicine,long presentation,long account,long user,long operator,long admin,long stock) {
        CurrentAccount userActor() { return new CurrentAccount(account,"USUARIO","test",0); }
        CurrentAccount operatorActor() { return new CurrentAccount(operator,"OPERADOR","test",0); }
        CurrentAccount adminActor() { return new CurrentAccount(admin,"ADMINISTRADOR","test",0); }
    }
    Fixture f;
    long create(String sql,Object... args) { return db.queryForObject(sql,Long.class,args); }
    int count(String sql,Object... args) { return db.queryForObject(sql,Integer.class,args); }
    TransactionTemplate tx() { return new TransactionTemplate(transactions); }
    Timestamp ts(Instant t) { return Timestamp.from(t); }
    UUID key() { return UUID.randomUUID(); }
    CrearReservaRequest cmd(int qty) { return new CrearReservaRequest(f.pharmacy(),f.presentation(),qty,null); }
    @BeforeEach void setup() {
        f=tx().execute(s->{
            String id=UUID.randomUUID().toString();
            long pharmacy=create("INSERT INTO farmacia(nombre,direccion) VALUES (?,?) RETURNING id_farmacia","F "+id,"Calle 123");
            long med=create("INSERT INTO medicamento(nombre) VALUES (?) RETURNING id_medicamento","M "+id);
            long presentation=create("INSERT INTO presentacion_medicamento(id_medicamento,concentracion_texto,forma_farmaceutica,cantidad_envase,unidad_expendio,condicion_dispensa) VALUES (?,'10 mg','comprimidos',10,'caja','VENTA_LIBRE') RETURNING id_presentacion",med);
            long operator=accounts.newAccount("op-"+id+"@test.org","hash","OPERADOR",T);
            db.update("INSERT INTO operador(id_cuenta,id_farmacia,nombre,apellido) VALUES (?,?,'Operador','Test')",operator,pharmacy);
            long admin=accounts.newAccount("admin-"+id+"@test.org","hash","ADMINISTRADOR",T);
            db.update("INSERT INTO administrador(id_cuenta,nombre,apellido) VALUES (?,'Admin','Test')",admin);
            long account=accounts.newAccount("user-"+id+"@test.org","hash","USUARIO",T);
            long user=accounts.newUser(account,"Nombre","Apellido");
            long stock=create("INSERT INTO stock(id_farmacia,id_presentacion,cantidad_disponible,fecha_hora_actualizacion) VALUES (?,?,10,?) RETURNING id_stock",pharmacy,presentation,ts(T));
            return new Fixture(pharmacy,med,presentation,account,user,operator,admin,stock);
        });
    }
    ReservaDTO created(int qty) { return reservations.crear(cmd(qty),key(),f.userActor(),null).dto(); }
    @Test void createIsAtomicAndProducesMovementAndSnapshots() {
        var result=created(2);
        assertEquals("ACTIVA",result.estado());
        assertEquals(12,result.codigoVisible().length());
        assertEquals(8,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",f.stock()));
        assertEquals(1,count("SELECT count(*) FROM movimiento_stock WHERE id_reserva=? AND tipo='RESERVA_CONFIRMADA'",result.id()));
        assertEquals(1,count("SELECT count(*) FROM idempotency_record WHERE id_cuenta=?",f.account()));
        assertEquals(T.plus(Duration.ofHours(4)).atZone(ZoneId.of("America/Argentina/Buenos_Aires")).toOffsetDateTime().getOffset().toString(),"-03:00");
        assertNotNull(result.farmaciaNombreSnapshot());
    }
    @Test void sameIdempotencyKeyReplaysOriginal201EvenAfterCancellation() {
        UUID id=key(); var request=cmd(1);
        var one=reservations.crear(request,id,f.userActor(),null).dto();
        reservations.cancelarPropia(one.codigoVisible(),f.userActor(),null);
        var replay=reservations.crear(request,id,f.userActor(),null).dto();
        assertEquals(one,replay);
        assertEquals(1,count("SELECT count(*) FROM reserva WHERE id_usuario=?",f.user()));
        assertEquals(10,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",f.stock()));
    }
    @Test void idempotencyKeyWithDifferentPayloadIsRejected() {
        UUID id=key(); reservations.crear(cmd(1),id,f.userActor(),null);
        var err=assertThrows(ar.edu.uade.medlink.common.error.ApiException.class,
            ()->reservations.crear(cmd(2),id,f.userActor(),null));
        assertEquals("IDEMPOTENCY_KEY_REUSED",err.codigo());
    }
    @Test void otcLimitAndGlobalLimitDoNotMutateStockOnRejection() {
        created(3);
        var ex=assertThrows(ar.edu.uade.medlink.common.error.ApiException.class,()->created(1));
        assertEquals("MAX_OTC_UNITS_EXCEEDED",ex.codigo());
        assertEquals(7,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",f.stock()));
    }
    @Test void ownCancellationReleasesExactlyOnceAndHasNoSuspension() {
        var r=created(2);
        var result=reservations.cancelarPropia(r.codigoVisible().toLowerCase(Locale.ROOT),f.userActor(),null);
        assertFalse(result.expired()); assertEquals("CANCELADA",result.dto().estado());
        assertEquals(10,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",f.stock()));
        assertEquals(1,count("SELECT count(*) FROM movimiento_stock WHERE id_reserva=? AND tipo='CANCELACION_RESTITUCION'",r.id()));
        assertEquals(0,count("SELECT count(*) FROM suspension WHERE id_usuario=?",f.user()));
        assertEquals("INVALID_STATE_TRANSITION",assertThrows(ar.edu.uade.medlink.common.error.ApiException.class,
            ()->reservations.cancelarPropia(r.codigoVisible(),f.userActor(),null)).codigo());
    }
    @Test void operatorCompletesWithoutRedeductingStock() {
        var r=created(1);
        assertEquals("COMPLETADA",reservations.retirar(f.pharmacy(),r.codigoVisible(),f.operatorActor(),null).dto().estado());
        assertEquals(9,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",f.stock()));
        assertEquals(1,count("SELECT count(*) FROM auditoria WHERE accion='CONFIRMAR_RETIRO' AND entidad_id=?",r.id()));
    }
    @Test void adminCannotCompleteButCanCancelOperativelyWithoutRestitution() {
        var r=created(1);
        assertEquals("FORBIDDEN",assertThrows(ar.edu.uade.medlink.common.error.ApiException.class,
            ()->reservations.retirar(f.pharmacy(),r.codigoVisible(),f.adminActor(),null)).codigo());
        var canceled=reservations.cancelarOperativa(f.pharmacy(),r.codigoVisible(),
            new CancelacionOperativaRequest("PRODUCTO_NO_DISPONIBLE","Dañado",null,null),f.adminActor(),null);
        assertEquals("CANCELADA",canceled.dto().estado());
        assertEquals(9,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",f.stock()));
    }
    @Test void manualRealStockCorrectionIsAbsoluteAndAudited() {
        var r=created(1);
        var result=reservations.cancelarOperativa(f.pharmacy(),r.codigoVisible(),
            new CancelacionOperativaRequest("STOCK_INCONSISTENTE",null,null,5),f.operatorActor(),null);
        assertEquals("CANCELADA",result.dto().estado());
        assertEquals(5,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",f.stock()));
        assertEquals(1,count("SELECT count(*) FROM movimiento_stock WHERE id_reserva=? AND tipo='CORRECCION_STOCK_INCONSISTENTE'",r.id()));
    }
    @Test void concurrentLastStockUnitIsNeverOversold() throws Exception {
        db.update("UPDATE stock SET cantidad_disponible=1 WHERE id_stock=?",f.stock());
        long second=tx().execute(s->{
            long a=accounts.newAccount("second-"+UUID.randomUUID()+"@test.org","hash","USUARIO",T);
            accounts.newUser(a,"Otro","Usuario");return a;
        });
        ExecutorService pool=Executors.newFixedThreadPool(2);
        var start=new CountDownLatch(1);
        try {
            Callable<Boolean> a=()->{
                start.await();
                try { reservations.crear(cmd(1),key(),f.userActor(),null); return true; }
                catch(ar.edu.uade.medlink.common.error.ApiException ex) {
                    assertEquals("INSUFFICIENT_STOCK",ex.codigo());return false;
                }
            };
            Callable<Boolean> b=()->{
                start.await();
                try { reservations.crear(cmd(1),key(),new CurrentAccount(second,"USUARIO","test",0),null);return true; }
                catch(ar.edu.uade.medlink.common.error.ApiException ex) {
                    assertEquals("INSUFFICIENT_STOCK",ex.codigo());return false;
                }
            };
            var first=pool.submit(a); var other=pool.submit(b); start.countDown();
            assertEquals(1,(first.get(20,TimeUnit.SECONDS)?1:0)+(other.get(20,TimeUnit.SECONDS)?1:0));
            assertEquals(0,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",f.stock()));
        } finally { pool.shutdownNow(); }
    }
    @Test void visibilityIsRestrictedByUserAndOperatorScope() {
        var r=created(1);
        assertEquals(1,reservations.propias(0,20,"ACTIVA",List.of(),f.userActor()).totalElements());
        assertNull(reservations.detalleFarmacia(f.pharmacy(),r.codigoVisible(),f.operatorActor()).prescripcionCodigo());
        long second=tx().execute(s->{
            long a=accounts.newAccount("other-"+UUID.randomUUID()+"@test.org","hash","USUARIO",T);
            accounts.newUser(a,"Otro","Usuario");return a;
        });
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(ar.edu.uade.medlink.common.error.ApiException.class,
            ()->reservations.propia(r.codigoVisible(),new CurrentAccount(second,"USUARIO","test",0))).codigo());
    }
    @Test void expiredReservationIsEffectiveBeforeMaterializationAndReleaseIsOnce() {
        var old=tx().execute(s->{
            var expiration=T.minusSeconds(1);
            return create("""
                INSERT INTO reserva(codigo_visible,id_usuario,id_farmacia,id_presentacion,cantidad,
                 fecha_hora_creacion,fecha_hora_vencimiento,farmacia_nombre_snapshot,farmacia_direccion_snapshot,
                 medicamento_nombre_snapshot,presentacion_descripcion_snapshot)
                VALUES (?,?,?, ?,1,?,?,'Farmacia','Direccion','Medicamento','Presentacion') RETURNING id_reserva
                """, UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT), f.user(),f.pharmacy(),f.presentation(),ts(expiration.minusSeconds(14400)),ts(expiration));
        });
        db.update("UPDATE stock SET cantidad_disponible=9 WHERE id_stock=?",f.stock());
        // Historical reservation was confirmed earlier; movement is part of the fixture.
        db.update("INSERT INTO movimiento_stock(id_stock,id_reserva,tipo,cantidad_anterior,cantidad_nueva,delta,fecha_hora) " +
            "VALUES (?,?,'RESERVA_CONFIRMADA',10,9,-1,?)",f.stock(),old,ts(T.minusSeconds(14401)));
        assertEquals(1,reservations.propias(0,20,"VENCIDA",List.of(),f.userActor()).totalElements());
        reservations.materializar(old,null);
        reservations.materializar(old,null);
        assertEquals(10,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",f.stock()));
        assertEquals(1,count("SELECT count(*) FROM movimiento_stock WHERE id_reserva=? AND tipo='VENCIMIENTO'",old));
    }
    record RxFixture(long presentation,long stock,long prescription,long item,String code) { }
    RxFixture rx(int authorized) {
        return tx().execute(s->{
            String code="RX-"+UUID.randomUUID();
            long p=create("""
                INSERT INTO presentacion_medicamento(id_medicamento,concentracion_texto,forma_farmaceutica,
                    cantidad_envase,unidad_expendio,condicion_dispensa)
                VALUES (?,'20 mg','ampollas',1,'frasco','BAJO_RECETA') RETURNING id_presentacion
                """,f.medicine());
            long stock=create("""
                INSERT INTO stock(id_farmacia,id_presentacion,cantidad_disponible,fecha_hora_actualizacion)
                VALUES (?,?,10,?) RETURNING id_stock
                """,f.pharmacy(),p,ts(T));
            long prescription=create("""
                INSERT INTO prescripcion(codigo_externo,id_usuario,fecha_emision,fecha_vencimiento,fecha_hora_creacion)
                VALUES (?,?,?,?,?) RETURNING id_prescripcion
                """,code,f.user(),LocalDate.of(2026,10,1),LocalDate.of(2026,10,27),ts(T));
            long item=create("""
                INSERT INTO item_prescripcion(id_prescripcion,id_presentacion,cantidad_autorizada)
                VALUES (?,?,?) RETURNING id_item_prescripcion
                """,prescription,p,authorized);
            return new RxFixture(p,stock,prescription,item,code);
        });
    }
    CrearReservaRequest rxCmd(RxFixture rx,int quantity) {
        return new CrearReservaRequest(f.pharmacy(),rx.presentation(),quantity,rx.code());
    }
    @Test void prescriptionCommitCancellationAndCompletionConserveAuthorizedQuantity() {
        RxFixture rx=rx(3);
        var first=reservations.crear(rxCmd(rx,2),key(),f.userActor(),null).dto();
        assertEquals(2,count("SELECT cantidad_comprometida FROM item_prescripcion WHERE id_item_prescripcion=?",rx.item()));
        assertEquals("PRESCRIPTION_QUANTITY_INSUFFICIENT",assertThrows(
            ar.edu.uade.medlink.common.error.ApiException.class,
            ()->reservations.crear(rxCmd(rx,2),key(),f.userActor(),null)).codigo());
        assertEquals(8,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",rx.stock()));
        reservations.cancelarPropia(first.codigoVisible(),f.userActor(),null);
        assertEquals(0,count("SELECT cantidad_comprometida FROM item_prescripcion WHERE id_item_prescripcion=?",rx.item()));
        var second=reservations.crear(rxCmd(rx,3),key(),f.userActor(),null).dto();
        // Existing authorization survives later annulment (RN-29).
        db.update("UPDATE prescripcion SET estado='ANULADA' WHERE id_prescripcion=?",rx.prescription());
        reservations.retirar(f.pharmacy(),second.codigoVisible(),f.operatorActor(),null);
        assertEquals(0,count("SELECT cantidad_comprometida FROM item_prescripcion WHERE id_item_prescripcion=?",rx.item()));
        assertEquals(3,count("SELECT cantidad_utilizada FROM item_prescripcion WHERE id_item_prescripcion=?",rx.item()));
        assertEquals(7,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",rx.stock()));
        assertEquals("PRESCRIPTION_INVALID",assertThrows(ar.edu.uade.medlink.common.error.ApiException.class,
            ()->reservations.crear(rxCmd(rx,1),key(),f.userActor(),null)).codigo());
    }
    @Test void suspendedUserCannotReserveAndSuspensionStillVisibleAfterReplay() {
        long from=T.minus(Duration.ofDays(2)).toEpochMilli();
        db.update("INSERT INTO suspension(id_usuario,fecha_hora_inicio,fecha_hora_fin,motivo) " +
            "VALUES (?,?,?,'INCUMPLIMIENTOS_REITERADOS')",f.user(),ts(Instant.ofEpochMilli(from)),
            ts(Instant.ofEpochMilli(from).plus(Duration.ofDays(7))));
        var result=reservations.crear(cmd(1),key(),f.userActor(),null);
        assertTrue(result.suspended());
        assertNull(result.dto());
        assertEquals(10,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",f.stock()));
        assertEquals(0,count("SELECT count(*) FROM idempotency_record WHERE id_cuenta=?",f.account()));
    }
    @Test void globalLimitIsCountedAcrossDifferentPresentations() {
        for (int i=0;i<5;i++) {
            long p=create("""
                INSERT INTO presentacion_medicamento(id_medicamento,concentracion_texto,forma_farmaceutica,
                    cantidad_envase,unidad_expendio,condicion_dispensa)
                VALUES (?,?,'comprimidos',10,'caja','VENTA_LIBRE') RETURNING id_presentacion
                """,f.medicine(),(i+10)+" mg");
            db.update("INSERT INTO stock(id_farmacia,id_presentacion,cantidad_disponible,fecha_hora_actualizacion) VALUES (?,?,10,?)",f.pharmacy(),p,ts(T));
            var dto=reservations.crear(new CrearReservaRequest(f.pharmacy(),p,1,null),key(),f.userActor(),null).dto();
            assertEquals("ACTIVA",dto.estado());
        }
        assertEquals("MAX_ACTIVE_RESERVATIONS_EXCEEDED",assertThrows(
            ar.edu.uade.medlink.common.error.ApiException.class,
            ()->reservations.crear(cmd(1),key(),f.userActor(),null)).codigo());
        assertEquals(10,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",f.stock()));
    }
    @Test void operationalCancellationRequiresStructuredRestitutionChoice() {
        var original=created(1);
        var rejected=assertThrows(ar.edu.uade.medlink.common.error.ApiException.class,()->
            reservations.cancelarOperativa(f.pharmacy(),original.codigoVisible(),
                new CancelacionOperativaRequest("OTRA_CAUSA","Error", "INVALIDO",null),f.adminActor(),null));
        assertEquals("VALIDATION_ERROR",rejected.codigo());
        assertEquals("CANCELADA",reservations.cancelarOperativa(f.pharmacy(),original.codigoVisible(),
            new CancelacionOperativaRequest("OTRA_CAUSA","Error de carga","RESTITUIR",null),f.adminActor(),null).dto().estado());
        assertEquals(10,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",f.stock()));
        var other=created(1);
        reservations.cancelarOperativa(f.pharmacy(),other.codigoVisible(),
            new CancelacionOperativaRequest("OTRA_CAUSA","Error de carga","NO_RESTITUIR",null),f.adminActor(),null);
        assertEquals(9,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",f.stock()));
    }
    @Test void concurrentSameIdempotencyKeyCreatesExactlyOneReservation() throws Exception {
        UUID key=key(); var command=cmd(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch go=new CountDownLatch(1);
        try {
            Callable<ReservaDTO> create=()->{go.await();return reservations.crear(command,key,f.userActor(),null).dto();};
            Future<ReservaDTO> first=pool.submit(create), second=pool.submit(create);
            go.countDown();
            assertEquals(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
            assertEquals(1,count("SELECT count(*) FROM reserva WHERE id_usuario=?",f.user()));
            assertEquals(1,count("SELECT count(*) FROM idempotency_record WHERE id_cuenta=?",f.account()));
            assertEquals(9,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",f.stock()));
        } finally {pool.shutdownNow();}
    }
    @Test void failedReservationDoesNotConsumeIdempotencyKey() {
        UUID id=key();
        db.update("UPDATE stock SET cantidad_disponible=0 WHERE id_stock=?",f.stock());
        assertEquals("INSUFFICIENT_STOCK",assertThrows(ar.edu.uade.medlink.common.error.ApiException.class,
            ()->reservations.crear(cmd(1),id,f.userActor(),null)).codigo());
        assertEquals(0,count("SELECT count(*) FROM idempotency_record WHERE id_cuenta=?",f.account()));
        db.update("UPDATE stock SET cantidad_disponible=1 WHERE id_stock=?",f.stock());
        assertNotNull(reservations.crear(cmd(1),id,f.userActor(),null).dto());
    }
    @Test void adminAndOperatorPermissionsAreSeparated() {
        var r=created(1);
        assertEquals(1,reservations.historialAdmin(f.user(),null,null,null,0,20,List.of(),f.adminActor()).totalElements());
        assertEquals("FORBIDDEN",assertThrows(ar.edu.uade.medlink.common.error.ApiException.class,()->
            reservations.historialAdmin(f.user(),null,null,null,0,20,List.of(),f.operatorActor())).codigo());
        assertEquals("FORBIDDEN",assertThrows(ar.edu.uade.medlink.common.error.ApiException.class,()->
            reservations.retirar(f.pharmacy(),r.codigoVisible(),f.adminActor(),null)).codigo());
    }
    @Test void inconsistentStockCorrectionRespectsPendingExpiry() {
        var active=created(1);
        // A separate, already-expired reservation has not yet been materialized.
        long old=tx().execute(s->create("""
            INSERT INTO reserva(codigo_visible,id_usuario,id_farmacia,id_presentacion,cantidad,
                fecha_hora_creacion,fecha_hora_vencimiento,farmacia_nombre_snapshot,
                farmacia_direccion_snapshot,medicamento_nombre_snapshot,presentacion_descripcion_snapshot)
            VALUES (?,?,?,?,1,?,?,'Farmacia','Calle 123','Medicamento','Descripcion') RETURNING id_reserva
            """,UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT),f.user(),f.pharmacy(),f.presentation(),ts(T.minus(Duration.ofHours(5))),ts(T.minus(Duration.ofHours(1)))));
        db.update("UPDATE stock SET cantidad_disponible=8 WHERE id_stock=?",f.stock());
        db.update("INSERT INTO movimiento_stock(id_stock,id_reserva,tipo,cantidad_anterior,cantidad_nueva,delta,fecha_hora) " +
            "VALUES (?,?,'RESERVA_CONFIRMADA',9,8,-1,?)",f.stock(),old,ts(T.minus(Duration.ofHours(5))));
        var result=reservations.cancelarOperativa(f.pharmacy(),active.codigoVisible(),
            new CancelacionOperativaRequest("STOCK_INCONSISTENTE",null,null,5),f.operatorActor(),null);
        assertEquals("CANCELADA",result.dto().estado());
        assertEquals(5,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",f.stock()));
        assertEquals("VENCIDA",db.queryForObject("SELECT estado_persistido::text FROM reserva WHERE id_reserva=?",String.class,old));
        assertEquals(1,count("SELECT count(*) FROM movimiento_stock WHERE id_reserva=? AND tipo='VENCIMIENTO'",old));
    }
    @Test void concurrentSamePrescriptionItemNeverExceedsAuthorization() throws Exception {
        RxFixture rx=rx(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch go=new CountDownLatch(1);
        try {
            Callable<Boolean> call=()->{
                go.await();
                try { reservations.crear(rxCmd(rx,1),key(),f.userActor(),null); return true; }
                catch(ar.edu.uade.medlink.common.error.ApiException err) {
                    assertEquals("PRESCRIPTION_QUANTITY_INSUFFICIENT",err.codigo()); return false;
                }
            };
            var first=pool.submit(call); var second=pool.submit(call); go.countDown();
            assertEquals(1,(first.get(30,TimeUnit.SECONDS)?1:0)+(second.get(30,TimeUnit.SECONDS)?1:0));
            assertEquals(1,count("SELECT cantidad_comprometida FROM item_prescripcion WHERE id_item_prescripcion=?",rx.item()));
            assertEquals(9,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",rx.stock()));
        } finally {pool.shutdownNow();}
    }
    @Test void expiredPrescriptionReservationReleasesAuthorizationExactlyOnce() {
        RxFixture rx=rx(2);
        long reservation=tx().execute(s->{
            db.update("UPDATE item_prescripcion SET cantidad_comprometida=1 WHERE id_item_prescripcion=?",rx.item());
            db.update("UPDATE stock SET cantidad_disponible=9 WHERE id_stock=?",rx.stock());
            var code=UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);
            long id=create("""
                INSERT INTO reserva(codigo_visible,id_usuario,id_farmacia,id_presentacion,id_item_prescripcion,
                    cantidad,fecha_hora_creacion,fecha_hora_vencimiento,farmacia_nombre_snapshot,
                    farmacia_direccion_snapshot,medicamento_nombre_snapshot,presentacion_descripcion_snapshot)
                VALUES (?,?,?,?,?,1,?,?,'Farmacia','Calle 123','Medicamento','Descripcion') RETURNING id_reserva
                """,code,f.user(),f.pharmacy(),rx.presentation(),rx.item(),
                ts(T.minus(Duration.ofHours(5))),ts(T.minus(Duration.ofHours(1))));
            db.update("INSERT INTO movimiento_stock(id_stock,id_reserva,tipo,cantidad_anterior,cantidad_nueva,delta,fecha_hora) " +
                "VALUES (?,?,'RESERVA_CONFIRMADA',10,9,-1,?)",rx.stock(),id,ts(T.minus(Duration.ofHours(5))));
            return id;
        });
        reservations.materializar(reservation,null);
        reservations.materializar(reservation,null);
        assertEquals(10,count("SELECT cantidad_disponible FROM stock WHERE id_stock=?",rx.stock()));
        assertEquals(0,count("SELECT cantidad_comprometida FROM item_prescripcion WHERE id_item_prescripcion=?",rx.item()));
        assertEquals(1,count("SELECT count(*) FROM movimiento_stock WHERE id_reserva=? AND tipo='VENCIMIENTO'",reservation));
        assertEquals("VENCIDA",db.queryForObject("SELECT estado_persistido::text FROM reserva WHERE id_reserva=?",String.class,reservation));
    }
}
