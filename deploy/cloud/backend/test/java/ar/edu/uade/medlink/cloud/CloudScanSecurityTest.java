package ar.edu.uade.medlink.cloud;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.escaneo.PrescriptionImageExtractor;
import ar.edu.uade.medlink.recetademo.RecetaDemoService;
import ar.edu.uade.medlink.recetademo.RecetaDemoDtos;
import ar.edu.uade.medlink.security.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static ar.edu.uade.medlink.escaneo.PrescriptionImageExtractor.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Every write is synthetic, in this class's own disposable PostgreSQL container. No external DB override. */
@SpringBootTest(properties={"medlink.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "medlink.reservas.scheduler.enabled=false","medlink.recetas-demo.scan.enabled=true",
    "medlink.cloud.scan-allowed-account-ids=1","medlink.cloud.scan-daily-limit=2"})
@ActiveProfiles("cloud") @AutoConfigureMockMvc
class CloudScanSecurityTest {
    static final PostgreSQLContainer PG=new PostgreSQLContainer("postgres:18-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        PG.start(); r.add("spring.datasource.url",PG::getJdbcUrl);
        r.add("spring.datasource.username",PG::getUsername);r.add("spring.datasource.password",PG::getPassword);
    }
    @AfterAll static void stop() { PG.stop(); }
    @Autowired JdbcTemplate db; @Autowired MockMvc mvc; @Autowired JwtTokens tokens;
    @Autowired AccountRepository accounts; @Autowired CloudScanPolicy policy;
    @Autowired CloudScanBudget budget; @Autowired PlatformTransactionManager tx;
    @Autowired RecetaDemoService recipes; @Autowired Clock clock;
    @MockitoBean PrescriptionImageExtractor extractor;
    CurrentAccount patient,other,admin,operator;
    long presentation,pharmacy;
    @BeforeEach void fixtures() {
        assertTrue(db.getDataSource()!=null && PG.isRunning());
        db.execute("TRUNCATE cuenta RESTART IDENTITY CASCADE");
        db.execute("TRUNCATE cloud_scan_budget");db.update("UPDATE cloud_scan_lease SET owner=NULL,expires_at=NULL");
        ReflectionTestUtils.setField(policy,"enabled",true);
        reset(extractor);when(extractor.disponible()).thenReturn(true);
        new TransactionTemplate(tx).executeWithoutResult(ignored -> {
        patient=account("USUARIO");other=account("USUARIO");admin=account("ADMINISTRADOR");operator=account("OPERADOR");
        assertEquals(1,patient.id());
        accounts.newUser(patient.id(),"Paciente","Sintetico");accounts.newUser(other.id(),"Otro","Sintetico");
        db.update("INSERT INTO administrador(id_cuenta,nombre,apellido) VALUES (?,'Admin','Sintetico')",admin.id());
        pharmacy=id("INSERT INTO farmacia(nombre,direccion) VALUES (?,'Calle de prueba 123') RETURNING id_farmacia",UUID.randomUUID().toString());
        db.update("INSERT INTO operador(id_cuenta,id_farmacia,nombre,apellido) VALUES (?,?,'Operador','Sintetico')",operator.id(),pharmacy);
        long medicine=id("INSERT INTO medicamento(nombre) VALUES (?) RETURNING id_medicamento","Amoxicilina sintética "+UUID.randomUUID());
        presentation=id("INSERT INTO presentacion_medicamento(id_medicamento,concentracion_texto,forma_farmaceutica,cantidad_envase,unidad_expendio,condicion_dispensa) VALUES (?,'250 mg/5 ml','Polvo para suspensión',60,'Frasco','BAJO_RECETA') RETURNING id_presentacion",medicine);
        db.update("INSERT INTO stock(id_farmacia,id_presentacion,cantidad_disponible,fecha_hora_actualizacion) VALUES (?,?,10,?)",pharmacy,presentation,Timestamp.from(clock.instant()));
        });
    }
    CurrentAccount account(String role) {
        return new CurrentAccount(accounts.newAccount(UUID.randomUUID()+"@synthetic.test","unused",role,clock.instant()),role,"synthetic",0);
    }
    long id(String sql,Object... args) { return db.queryForObject(sql,Long.class,args); }
    String bearer(CurrentAccount actor) { return "Bearer "+tokens.create(accounts.byId(actor.id()).orElseThrow()); }
    int used() { return db.queryForObject("SELECT COALESCE(sum(attempts),0) FROM cloud_scan_budget",Integer.class); }
    MockMultipartFile image() throws Exception {
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(3,2,BufferedImage.TYPE_INT_RGB),"png",bytes);
        return new MockMultipartFile("archivo","synthetic.png","image/png",bytes.toByteArray());
    }
    org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder scan() throws Exception {
        return multipart("/api/me/recetas-demo/escaneo").file(image()).param("presentacionId",""+presentation).param("cantidad","1");
    }
    RecetaDemoDtos.Receta recipe() {
        return recipes.generar(new RecetaDemoDtos.GenerarLote(presentation,1,LocalDate.now(clock).plusDays(3)),admin,null).recetas().getFirst();
    }
    Lectura reading(RecetaDemoDtos.Receta r) {
        String secondary=db.queryForObject("SELECT codigo_verificacion FROM receta_demo WHERE id_receta_demo=?",String.class,r.id());
        return new Lectura(r.codigoReceta(),secondary,r.fechaEmision().toString(),r.fechaVencimiento().toString(),
            new Documento("RECETA ELECTRÓNICA SIMULADA","RECETA FICTICIA — SIN VALIDEZ MÉDICA","Profesional Demo MediLink","Centro Académico Ficticio MediLink","Titular pendiente de asignación","Ejercicio de reserva. No usar como indicación médica."),
            List.of(new Medicamento(r.medicamento(),r.concentracion(),r.presentacion(),1)),List.of(),Estado.COMPLETA);
    }
    @Test void authenticationRolesAllowlistAndAccountStateRejectBeforeBudget() throws Exception {
        mvc.perform(scan()).andExpect(status().isUnauthorized());
        mvc.perform(scan().header("Authorization","Bearer malformed")).andExpect(status().isUnauthorized());
        for(var actor:List.of(other,admin,operator)) mvc.perform(scan().header("Authorization",bearer(actor))).andExpect(status().isForbidden());
        String old=bearer(patient);accounts.changeState(patient.id(),"DESHABILITADA",true);
        mvc.perform(scan().header("Authorization",old)).andExpect(status().isForbidden());
        accounts.changeState(patient.id(),"ACTIVA",false);
        mvc.perform(scan().header("Authorization",old)).andExpect(status().isUnauthorized());
        assertEquals(0,used());verify(extractor,never()).extraer(any());
    }
    @Test void disabledOrUnconfiguredOrEmptyAccessConsumesNoAttempts() throws Exception {
        ReflectionTestUtils.setField(policy,"enabled",false);
        mvc.perform(scan().header("Authorization",bearer(patient))).andExpect(status().isServiceUnavailable());
        ReflectionTestUtils.setField(policy,"enabled",true);when(extractor.disponible()).thenReturn(false);
        mvc.perform(scan().header("Authorization",bearer(patient))).andExpect(status().isServiceUnavailable());
        when(extractor.disponible()).thenReturn(true);
        assertThrows(ApiException.class,()->new CloudScanPolicy(db,extractor,true,"").authorize(patient));
        assertThrows(IllegalArgumentException.class,()->new CloudScanPolicy(db,extractor,true,"1,2"));
        assertEquals(0,used());verify(extractor,never()).extraer(any());
    }
    @Test void configurationIsSpecificToAuthorizedPatient() throws Exception {
        mvc.perform(get("/api/me/recetas-demo/configuracion").header("Authorization",bearer(patient))).andExpect(jsonPath("$.escaneoHabilitado").value(true));
        mvc.perform(get("/api/me/recetas-demo/configuracion").header("Authorization",bearer(other))).andExpect(jsonPath("$.escaneoHabilitado").value(false));
        assertEquals(0,used());
    }
    @Test void suspendedPatientCannotConsumeBudget() throws Exception {
        long user=id("SELECT id_usuario FROM usuario WHERE id_cuenta=?",patient.id());
        var start=clock.instant().minusSeconds(5);
        db.update("INSERT INTO suspension(id_usuario,fecha_hora_inicio,fecha_hora_fin,motivo) VALUES (?,?,?,'INCUMPLIMIENTOS_REITERADOS')",
            user,Timestamp.from(start),Timestamp.from(start.plus(Duration.ofHours(168))));
        mvc.perform(scan().header("Authorization",bearer(patient))).andExpect(status().isForbidden());
        assertEquals(0,used());
    }
    @Test void revokedSessionCannotConsumeBudget() throws Exception {
        String token=tokens.create(accounts.byId(patient.id()).orElseThrow());var jwt=tokens.verify(token);
        accounts.revoke(jwt.getId(),patient.id(),jwt.getExpiresAt());
        mvc.perform(scan().header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        assertEquals(0,used());verify(extractor,never()).extraer(any());
    }
    @Test void twoAttemptsPersistAfterFailuresAndNewBudgetObject() throws Exception {
        when(extractor.extraer(any())).thenThrow(new ApiException(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT,"ESCANEO_TIMEOUT","Simulado"));
        for(int i=0;i<2;i++)mvc.perform(scan().header("Authorization",bearer(patient))).andExpect(status().isGatewayTimeout());
        mvc.perform(scan().header("Authorization",bearer(patient))).andExpect(status().isTooManyRequests());
        var restarted=new CloudScanBudget(db,2);
        assertThrows(ApiException.class,()->new TransactionTemplate(tx).execute(s->restarted.acquire()));
        assertEquals(2,used());verify(extractor,times(2)).extraer(any());
        assertNull(db.queryForObject("SELECT owner FROM cloud_scan_lease",UUID.class));
    }
    @Test void leaseIsSharedAcrossInstancesAndDoesNotRefundAttempts() throws Exception {
        var executor=Executors.newFixedThreadPool(8);var start=new CountDownLatch(1);
        try {
            var tasks=new ArrayList<Future<UUID>>();
            for(int i=0;i<8;i++)tasks.add(executor.submit(()->{start.await();try{return budget.acquire();}catch(ApiException ex){assertEquals(429,ex.status().value());return null;}}));
            start.countDown();var owners=new ArrayList<UUID>();
            for(var task:tasks){var owner=task.get(20,TimeUnit.SECONDS);if(owner!=null)owners.add(owner);}
            assertEquals(1,owners.size());assertEquals(1,used());
            var owner=owners.getFirst();budget.renew(owner);
            assertThrows(ApiException.class,()->new TransactionTemplate(tx).execute(s->new CloudScanBudget(db,2).acquire()));
            budget.release(UUID.randomUUID());assertThrows(ApiException.class,budget::acquire);
            db.update("UPDATE cloud_scan_lease SET expires_at=clock_timestamp()-INTERVAL '1 second'");
            UUID replacement=budget.acquire();assertEquals(2,used());
            assertThrows(ApiException.class,()->budget.renew(owner));
            budget.release(owner);assertThrows(ApiException.class,budget::acquire);
            budget.release(replacement);
        } finally { executor.shutdownNow(); }
    }
    @Test void invalidFileNeverReachesExtractorButCannotRefundItsAttempt() throws Exception {
        var invalid=new MockMultipartFile("archivo","bad.pdf","application/pdf","invalid".getBytes());
        mvc.perform(multipart("/api/me/recetas-demo/escaneo").file(invalid).header("Authorization",bearer(patient))).andExpect(status().isBadRequest());
        assertEquals(1,used());verify(extractor,never()).extraer(any());
    }
    @Test void matchingDocumentLinksWithoutAnyStockOrReservationMutation() throws Exception {
        var r=recipe();when(extractor.extraer(any())).thenReturn(reading(r));
        long before=id("SELECT count(*) FROM reserva");long stock=id("SELECT cantidad_disponible FROM stock WHERE id_farmacia=? AND id_presentacion=?",pharmacy,presentation);
        mvc.perform(scan().header("Authorization",bearer(patient))).andExpect(status().isOk());
        assertEquals(stock,id("SELECT cantidad_disponible FROM stock WHERE id_farmacia=? AND id_presentacion=?",pharmacy,presentation));
        assertEquals(before,id("SELECT count(*) FROM reserva"));
        assertEquals(1,used());assertEquals(1,id("SELECT count(*) FROM receta_adjunta"));
        assertEquals(0,id("SELECT cantidad_comprometida+cantidad_utilizada FROM item_prescripcion"));
    }
    @Test void foreignOrAlteredDocumentIsRejectedWithoutStockMutation() throws Exception {
        var r=recipe();recipes.vincular(r.codigoReceta(),other,null);when(extractor.extraer(any())).thenReturn(reading(r));
        mvc.perform(scan().header("Authorization",bearer(patient))).andExpect(status().isNotFound());
        var own=recipe();var good=reading(own);
        when(extractor.extraer(any())).thenReturn(new Lectura(good.codigoReceta(),"MED-ALTERADO",good.fechaEmision(),good.fechaVencimiento(),good.documento(),good.medicamentos(),good.camposFaltantes(),good.estado()));
        mvc.perform(scan().header("Authorization",bearer(patient))).andExpect(status().isConflict());
        assertEquals(10,id("SELECT cantidad_disponible FROM stock WHERE id_farmacia=? AND id_presentacion=?",pharmacy,presentation));
        assertEquals(0,id("SELECT count(*) FROM receta_adjunta"));
    }
    @Test void expiredDocumentAndWrongPresentationCannotAuthorizeReservation() throws Exception {
        var original=recipe();var today=LocalDate.now(clock);
        String code="MLD-"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);
        long expiredId=id("""
            INSERT INTO receta_demo(lote,codigo,codigo_verificacion,fecha_emision,fecha_vencimiento,fecha_creacion,
                id_presentacion,medicamento,concentracion,presentacion)
            SELECT ?,?,'MED-1234-5678-9ABC',?,?,?,id_presentacion,medicamento,concentracion,presentacion
            FROM receta_demo WHERE id_receta_demo=? RETURNING id_receta_demo
            """,UUID.randomUUID(),code,today.minusDays(3),today.minusDays(1),Timestamp.from(clock.instant().minusSeconds(259200)),original.id());
        var expired=new RecetaDemoDtos.Receta(expiredId,code,"DISPONIBLE",today.minusDays(3),today.minusDays(1),presentation,original.medicamento(),original.concentracion(),original.presentacion(),1);
        when(extractor.extraer(any())).thenReturn(reading(expired));
        mvc.perform(scan().header("Authorization",bearer(patient))).andExpect(status().isConflict());
        when(extractor.extraer(any())).thenReturn(reading(original));
        long different=id("""
            INSERT INTO presentacion_medicamento(id_medicamento,concentracion_texto,forma_farmaceutica,cantidad_envase,unidad_expendio,condicion_dispensa)
            SELECT id_medicamento,'500 mg','Comprimidos',10,'Caja','BAJO_RECETA' FROM presentacion_medicamento
            WHERE id_presentacion=? RETURNING id_presentacion
            """,presentation);
        mvc.perform(multipart("/api/me/recetas-demo/escaneo").file(image()).param("presentacionId",""+different).param("cantidad","1").header("Authorization",bearer(patient)))
            .andExpect(status().isConflict());
        assertEquals(10,id("SELECT cantidad_disponible FROM stock WHERE id_farmacia=? AND id_presentacion=?",pharmacy,presentation));
        assertEquals(0,id("SELECT count(*) FROM receta_adjunta"));assertEquals(0,id("SELECT count(*) FROM reserva"));
    }
    @Test void availabilityIsPublicButReservationWithoutPrescriptionIsDenied() throws Exception {
        mvc.perform(get("/api/presentaciones/"+presentation+"/disponibilidad")).andExpect(status().isOk()).andExpect(jsonPath("$[0].farmaciaId").value(pharmacy));
        mvc.perform(post("/api/reservas").header("Authorization",bearer(patient)).header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
            .content("{\"farmaciaId\":"+pharmacy+",\"presentacionId\":"+presentation+",\"cantidad\":1}"))
            .andExpect(status().is4xxClientError());
        assertEquals(10,id("SELECT cantidad_disponible FROM stock WHERE id_farmacia=? AND id_presentacion=?",pharmacy,presentation));
        assertEquals(0,id("SELECT count(*) FROM reserva"));assertEquals(0,used());
    }
}
