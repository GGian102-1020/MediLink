package ar.edu.uade.medlink.stock;

import ar.edu.uade.medlink.admin.*;
import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.farmacia.dto.FarmaciaRequest;
import ar.edu.uade.medlink.farmacia.entity.EstadoFarmacia;
import ar.edu.uade.medlink.farmacia.repository.FarmaciaRepository;
import ar.edu.uade.medlink.farmacia.service.FarmaciaService;
import ar.edu.uade.medlink.security.*;
import ar.edu.uade.medlink.stock.dto.AjusteStockRequest;
import ar.edu.uade.medlink.stock.service.StockService;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL only. Uses Testcontainers by default, or a dedicated disposable external DB. */
@SpringBootTest(properties={"medlink.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.flyway.baseline-on-migrate=false"})
@AutoConfigureMockMvc
@Import(FarmaciaStockPostgresTest.TimeConfiguration.class)
@EnabledIf("postgresDisponible")
class FarmaciaStockPostgresTest {
    static final Instant T=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    static PostgreSQLContainer container;
    static boolean postgresDisponible() {
        return System.getenv("MEDLINK_TEST_DB_URL")!=null || DockerClientFactory.instance().isDockerAvailable();
    }
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry properties) {
        String url=System.getenv("MEDLINK_TEST_DB_URL");
        if (url==null) {
            container=new PostgreSQLContainer("postgres:16-alpine"); container.start();
            properties.add("spring.datasource.url",container::getJdbcUrl);
            properties.add("spring.datasource.username",container::getUsername);
            properties.add("spring.datasource.password",container::getPassword);
        } else {
            properties.add("spring.datasource.url",()->url);
            properties.add("spring.datasource.username",()->System.getenv().getOrDefault("MEDLINK_TEST_DB_USER","postgres"));
            properties.add("spring.datasource.password",()->System.getenv().getOrDefault("MEDLINK_TEST_DB_PASSWORD",""));
        }
    }
    @AfterAll static void stopContainer() { if (container!=null) container.stop(); }
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary Clock testClock() { return Clock.fixed(T,ZoneOffset.UTC); }
    }
    @Autowired JdbcTemplate db;
    @Autowired StockService stocks;
    @Autowired FarmaciaService farmacias;
    @Autowired FarmaciaRepository farmaciaRepository;
    @Autowired AccountRepository accounts;
    @Autowired JwtTokens tokens;
    @Autowired PlatformTransactionManager txManager;
    @Autowired MockMvc mvc;
    @MockitoSpyBean AuditService audit;
    Fixture f;
    record Fixture(long pharmacy,long medicine,long presentation,long admin,long operator,long userAccount,long user) {
        CurrentAccount adminActor() { return new CurrentAccount(admin,"ADMINISTRADOR","test",0); }
        CurrentAccount operatorActor() { return new CurrentAccount(operator,"OPERADOR","test",0); }
    }
    long insert(String sql,Object... args) { return db.queryForObject(sql,Long.class,args); }
    int count(String sql,Object... args) { return db.queryForObject(sql,Integer.class,args); }
    Timestamp at(Instant value) { return Timestamp.from(value); }
    TransactionTemplate transaction() { return new TransactionTemplate(txManager); }
    @BeforeEach void fixture() {
        f=transaction().execute(status->{
            String suffix=UUID.randomUUID().toString();
            long pharmacy=insert("INSERT INTO farmacia(nombre,direccion) VALUES (?,?) RETURNING id_farmacia","Prueba "+suffix,"Calle 123");
            long medicine=insert("INSERT INTO medicamento(nombre) VALUES (?) RETURNING id_medicamento","Medicamento "+suffix);
            long presentation=insert("INSERT INTO presentacion_medicamento(id_medicamento,concentracion_texto,forma_farmaceutica,cantidad_envase,unidad_expendio,condicion_dispensa) VALUES (?,'10 mg','comprimidos',10,'caja','VENTA_LIBRE') RETURNING id_presentacion",medicine);
            long admin=accounts.newAccount("admin"+suffix+"@test.local","hash","ADMINISTRADOR",T);
            db.update("INSERT INTO administrador(id_cuenta,nombre,apellido) VALUES (?,'Admin','Prueba')",admin);
            long operator=accounts.newAccount("operador"+suffix+"@test.local","hash","OPERADOR",T);
            db.update("INSERT INTO operador(id_cuenta,id_farmacia,nombre,apellido) VALUES (?,?,'Operador','Prueba')",operator,pharmacy);
            long userAccount=accounts.newAccount("usuario"+suffix+"@test.local","hash","USUARIO",T);
            long user=insert("INSERT INTO usuario(id_cuenta,nombre,apellido) VALUES (?,'Usuario','Prueba') RETURNING id_usuario",userAccount);
            return new Fixture(pharmacy,medicine,presentation,admin,operator,userAccount,user);
        });
    }
    String bearer(long account) { return "Bearer "+tokens.create(accounts.byId(account).orElseThrow()); }
    void adjust(int value) { stocks.ajustar(f.pharmacy(),f.presentation(),new AjusteStockRequest(value,"Conteo"),f.adminActor(),null); }
    int balance() { return count("SELECT cantidad_disponible FROM stock WHERE id_farmacia=? AND id_presentacion=?",f.pharmacy(),f.presentation()); }
    long reserve(Instant expires, long presentation, Long item) {
        return insert("""
            INSERT INTO reserva(codigo_visible,id_usuario,id_farmacia,id_presentacion,id_item_prescripcion,cantidad,
            fecha_hora_creacion,fecha_hora_vencimiento,farmacia_nombre_snapshot,farmacia_direccion_snapshot,
            medicamento_nombre_snapshot,presentacion_descripcion_snapshot)
            VALUES (?,?,?,?,?,1,?,?, 'Farmacia snapshot','Direccion snapshot','Medicamento snapshot','Presentacion snapshot') RETURNING id_reserva
            """,code(),f.user(),f.pharmacy(),presentation,item,at(expires.minusSeconds(14400)),at(expires));
    }
    String code() {
        var alphabet="0123456789ABCDEFGHJKMNPQRSTVWXYZ";
        var random=new java.security.SecureRandom();var code=new StringBuilder();
        for(int i=0;i<12;i++) code.append(alphabet.charAt(random.nextInt(32)));return code.toString();
    }
    @Test void flywayCreatesSchemaAndHibernateValidatesIt() {
        assertEquals(1,count("SELECT count(*) FROM flyway_schema_history WHERE version='1' AND success"));
        assertTrue(farmaciaRepository.findById(f.pharmacy()).isPresent());
    }
    @Test void pharmacyRestCreationEditionFiltersAndStateTransitions() throws Exception {
        String name="Nueva "+UUID.randomUUID();
        String body=mvc.perform(post("/api/admin/farmacias").header("Authorization",bearer(f.admin())).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\" "+name+" \" ,\"direccion\":\" Calle 99 \"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.nombre").value(name))
            .andExpect(jsonPath("$.estado").value("ACTIVA")).andExpect(jsonPath("$.operativa").value(false))
            .andReturn().getResponse().getContentAsString();
        long id=new tools.jackson.databind.ObjectMapper().readTree(body).get("id").asLong();
        mvc.perform(put("/api/admin/farmacias/"+id).header("Authorization",bearer(f.admin())).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Editada\",\"direccion\":\"Otra calle\",\"telefono\":\"123\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.telefono").value("123"));
        for(String action:List.of("desactivacion","reactivacion"))
            mvc.perform(post("/api/admin/farmacias/"+id+"/"+action).header("Authorization",bearer(f.admin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Cambio autorizado\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/farmacias").param("nombre",name).param("estado","ACTIVA").header("Authorization",bearer(f.admin())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertEquals(4,count("SELECT count(*) FROM auditoria WHERE tipo_entidad='FARMACIA' AND entidad_id=?",id));
    }
    @Test void deactivationUsesFunctionalExpiryAndReactivationPreservesStock() {
        adjust(3);reserve(T.plusMillis(1),f.presentation(),null);
        assertEquals("PHARMACY_HAS_ACTIVE_RESERVATIONS",assertThrows(ApiException.class,
            ()->farmacias.cambiarEstado(f.pharmacy(),false,"Cierre",f.adminActor(),null)).codigo());
        long other=transaction().execute(s->insert("INSERT INTO farmacia(nombre,direccion) VALUES ('Otra','Calle') RETURNING id_farmacia"));
        assertEquals(EstadoFarmacia.INACTIVA,farmacias.cambiarEstado(other,false,"Cierre",f.adminActor(),null).estado());
        assertEquals(3,balance());
    }
    @Test void absoluteAdjustmentPersistsMovementAuditAndRealTimestamp() {
        adjust(10);adjust(5);
        assertEquals(5,balance());
        assertEquals(1,count("SELECT count(*) FROM movimiento_stock m JOIN stock s ON s.id_stock=m.id_stock WHERE s.id_farmacia=? AND cantidad_anterior=10 AND cantidad_nueva=5 AND delta=-5 AND tipo='AJUSTE_MANUAL' AND actor_cuenta_id=? AND motivo='Conteo'",f.pharmacy(),f.admin()));
        assertEquals(2,count("SELECT count(*) FROM auditoria WHERE accion='AJUSTAR_STOCK' AND actor_cuenta_id=? AND resumen_posterior->>'cantidadDisponible' IN ('10','5')",f.admin()));
    }
    @Test void postgresEnforcesPairUniquenessNonNegativeBalanceAndImmutableHistory() {
        adjust(3);
        assertThrows(DataAccessException.class,()->db.update("INSERT INTO stock(id_farmacia,id_presentacion,cantidad_disponible,fecha_hora_actualizacion) VALUES (?,?,1,?)",f.pharmacy(),f.presentation(),at(T)));
        assertThrows(DataAccessException.class,()->db.update("UPDATE stock SET cantidad_disponible=-1 WHERE id_farmacia=?",f.pharmacy()));
        assertThrows(DataAccessException.class,()->db.update("UPDATE movimiento_stock SET motivo='Cambio' WHERE actor_cuenta_id=?",f.admin()));
        assertThrows(DataAccessException.class,()->db.update("DELETE FROM movimiento_stock WHERE actor_cuenta_id=?",f.admin()));
        assertEquals(3,balance());
    }
    @Test void realJwtRolesScopesAndInactivePharmacyRules() throws Exception {
        adjust(2);
        mvc.perform(get("/api/admin/farmacias").header("Authorization",bearer(f.operator()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/farmacias/"+f.pharmacy()+"/stock").header("Authorization",bearer(f.userAccount()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/farmacias/"+(f.pharmacy()+10000)+"/stock").header("Authorization",bearer(f.operator()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/farmacias/"+f.pharmacy()+"/stock").header("Authorization",bearer(f.operator())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].cantidadDisponible").value(2));
        farmacias.cambiarEstado(f.pharmacy(),false,"Cierre",f.adminActor(),null);
        mvc.perform(get("/api/farmacias/"+f.pharmacy()+"/stock/movimientos").header("Authorization",bearer(f.operator())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(put("/api/farmacias/"+f.pharmacy()+"/stock/"+f.presentation()).header("Authorization",bearer(f.operator()))
            .contentType(MediaType.APPLICATION_JSON).content("{\"cantidadDisponible\":5,\"motivo\":\"Conteo\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PHARMACY_INACTIVE"));
        farmacias.cambiarEstado(f.pharmacy(),true,"Apertura",f.adminActor(),null);assertEquals(2,balance());
    }
    @Test void stockRestHistoryIsPaginatedFilteredAndOrderedAndRejectsNegative() throws Exception {
        adjust(10);adjust(5);
        String root="/api/farmacias/"+f.pharmacy()+"/stock";
        mvc.perform(get(root+"/movimientos").header("Authorization",bearer(f.operator())).param("presentacionId",Long.toString(f.presentation()))
            .param("desde",T.toString()).param("hasta",T.plusMillis(1).toString()).param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].delta").value(-5)).andExpect(jsonPath("$.last").value(false));
        mvc.perform(put(root+"/"+f.presentation()).header("Authorization",bearer(f.operator())).contentType(MediaType.APPLICATION_JSON)
            .content("{\"cantidadDisponible\":-1,\"motivo\":\"Conteo\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
        mvc.perform(get(root+"/movimientos").header("Authorization",bearer(f.operator())).param("desde",T.toString()).param("hasta",T.toString()))
            .andExpect(status().isBadRequest());
        assertEquals(5,balance());
    }
    @Test void publicAvailabilityExcludesAbsentZeroInactiveAndNonOperationalStock() throws Exception {
        assertTrue(stocks.disponibilidad(f.presentation()).isEmpty());
        adjust(0);assertTrue(stocks.disponibilidad(f.presentation()).isEmpty());
        adjust(2);assertEquals(2,stocks.disponibilidad(f.presentation()).getFirst().cantidadDisponible());
        mvc.perform(get("/api/presentaciones/"+f.presentation()+"/disponibilidad")).andExpect(status().isOk());
        db.update("UPDATE cuenta SET estado='DESHABILITADA' WHERE id_cuenta=?",f.operator());
        assertTrue(stocks.disponibilidad(f.presentation()).isEmpty());
        // RN-04 permits Admin adjustment of an active pharmacy without an operational operator.
        adjust(4);assertEquals(4,balance());
        db.update("UPDATE cuenta SET estado='ACTIVA' WHERE id_cuenta=?",f.operator());
        db.update("UPDATE medicamento SET estado='INACTIVO' WHERE id_medicamento=?",f.medicine());
        assertTrue(stocks.disponibilidad(f.presentation()).isEmpty());
        assertEquals("MEDICATION_INACTIVE",assertThrows(ApiException.class,()->adjust(3)).codigo());
    }
    @Test void expiredReservationIsReleasedExactlyOnceBeforeAbsoluteCount() {
        adjust(0);long reservation=reserve(T,f.presentation(),null);
        adjust(5);adjust(5);
        assertEquals(5,balance());assertEquals("VENCIDA",db.queryForObject("SELECT estado_persistido::text FROM reserva WHERE id_reserva=?",String.class,reservation));
        assertEquals(1,count("SELECT count(*) FROM movimiento_stock WHERE id_reserva=? AND tipo='VENCIMIENTO'",reservation));
        assertEquals(1,count("SELECT count(*) FROM movimiento_stock m JOIN stock s ON s.id_stock=m.id_stock WHERE s.id_farmacia=? AND tipo='AJUSTE_MANUAL' AND cantidad_anterior=1 AND cantidad_nueva=5 AND delta=4",f.pharmacy()));
    }
    @Test void expirationReleasesPrescriptionCommitmentsAndEvaluatesSuspensions() {
        long rxPresentation=insert("INSERT INTO presentacion_medicamento(id_medicamento,concentracion_texto,forma_farmaceutica,cantidad_envase,unidad_expendio,condicion_dispensa) VALUES (?,'20 mg','comprimidos',10,'caja','BAJO_RECETA') RETURNING id_presentacion",f.medicine());
        stocks.ajustar(f.pharmacy(),rxPresentation,new AjusteStockRequest(0,"Inicial"),f.adminActor(),null);
        long prescription=insert("INSERT INTO prescripcion(codigo_externo,id_usuario,fecha_emision,fecha_vencimiento,fecha_hora_creacion) VALUES (?,?,current_date-1,current_date+1,?) RETURNING id_prescripcion",UUID.randomUUID().toString(),f.user(),at(T));
        long item=insert("INSERT INTO item_prescripcion(id_prescripcion,id_presentacion,cantidad_autorizada,cantidad_comprometida) VALUES (?,?,3,3) RETURNING id_item_prescripcion",prescription,rxPresentation);
        for(int i=0;i<3;i++) reserve(T.minusSeconds(i),rxPresentation,item);
        stocks.ajustar(f.pharmacy(),rxPresentation,new AjusteStockRequest(5,"Conteo"),f.adminActor(),null);
        assertEquals(0,count("SELECT cantidad_comprometida FROM item_prescripcion WHERE id_item_prescripcion=?",item));
        assertEquals(1,count("SELECT count(*) FROM suspension WHERE id_usuario=?",f.user()));
        assertEquals(1,count("SELECT count(*) FROM auditoria WHERE accion='CREAR_SUSPENSION' AND usuario_relacionado_id=? AND actor_rol='SISTEMA'",f.user()));
    }
    @Test void auditFailureRollsBackBalanceMovementAndExpirationTogether() {
        adjust(0);long reservation=reserve(T,f.presentation(),null);
        // Stub the spy behind the transactional proxy: setup itself is not a business transaction.
        AuditService auditTarget=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
        doThrow(new IllegalStateException("Simulated audit failure")).when(auditTarget).record(eq(f.admin()),eq("ADMINISTRADOR"),eq("AJUSTAR_STOCK"),eq("STOCK"),anyLong(),isNull(),anyString(),any(),anyMap(),isNull());
        assertThrows(IllegalStateException.class,()->adjust(5));
        assertEquals(0,balance());assertEquals("ACTIVA",db.queryForObject("SELECT estado_persistido::text FROM reserva WHERE id_reserva=?",String.class,reservation));
        assertEquals(0,count("SELECT count(*) FROM movimiento_stock WHERE id_reserva=?",reservation));
        assertEquals(1,count("SELECT count(*) FROM auditoria WHERE accion='AJUSTAR_STOCK' AND actor_cuenta_id=?",f.admin()));
    }
    @Test void expiredReservationsDoNotBlockPharmacyDeactivationAtTheExactBoundary() {
        adjust(0);reserve(T,f.presentation(),null);
        assertEquals(EstadoFarmacia.INACTIVA,farmacias.cambiarEstado(f.pharmacy(),false,"Cierre",f.adminActor(),null).estado());
        assertTrue(stocks.disponibilidad(f.presentation()).isEmpty());
    }
    @Test void inactivePresentationCannotBeAdjustedOrOfferedPublicly() {
        adjust(2);
        db.update("UPDATE presentacion_medicamento SET estado='INACTIVA' WHERE id_presentacion=?",f.presentation());
        assertEquals("PRESENTATION_INACTIVE",assertThrows(ApiException.class,()->adjust(5)).codigo());
        assertTrue(stocks.disponibilidad(f.presentation()).isEmpty());assertEquals(2,balance());
    }
    @Test void operatorAdjustsOwnPharmacyAndAuditRecordsOperatorIdentity() throws Exception {
        mvc.perform(put("/api/farmacias/"+f.pharmacy()+"/stock/"+f.presentation()).header("Authorization",bearer(f.operator()))
            .contentType(MediaType.APPLICATION_JSON).content("{\"cantidadDisponible\":5,\"motivo\":\"Conteo operador\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cantidadDisponible").value(5));
        assertEquals(5,balance());
        assertEquals(1,count("SELECT count(*) FROM auditoria WHERE actor_cuenta_id=? AND actor_rol='OPERADOR' AND accion='AJUSTAR_STOCK'",f.operator()));
    }
    @Test void adoptingSharedPopulatedSchemaRequiresExplicitBaselineAndPreservesData() throws Exception {
        String database="medlink_baseline_"+UUID.randomUUID().toString().replace("-","");
        db.execute("CREATE DATABASE "+database);
        String url;
        try(var source=db.getDataSource().getConnection()) { url=source.getMetaData().getURL(); }
        url=url.substring(0,url.lastIndexOf('/')+1)+database;
        String user=System.getenv().getOrDefault("MEDLINK_TEST_DB_USER",container==null?"postgres":container.getUsername());
        String password=System.getenv().getOrDefault("MEDLINK_TEST_DB_PASSWORD",container==null?"":container.getPassword());
        try(var connection=java.sql.DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
            statement.execute(java.nio.file.Files.readString(java.nio.file.Path.of("..","database","01_medlink_esquema.sql")));
            statement.execute("INSERT INTO farmacia(nombre,direccion) VALUES ('Preexistente','Direccion conservada')");
            final String target=url;
            assertThrows(org.flywaydb.core.api.FlywayException.class,()->org.flywaydb.core.Flyway.configure()
                .dataSource(target,user,password).load().migrate());
            org.flywaydb.core.Flyway.configure().dataSource(target,user,password).baselineOnMigrate(true)
                .baselineVersion("1").load().migrate();
            try(var rows=statement.executeQuery("SELECT count(*) FROM farmacia WHERE nombre='Preexistente' AND direccion='Direccion conservada'")) {
                assertTrue(rows.next());assertEquals(1,rows.getInt(1));
            }
            try(var rows=statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE version='1' AND type='BASELINE' AND success")) {
                assertTrue(rows.next());assertEquals(1,rows.getInt(1));
            }
        }
    }
    void simultaneous(Runnable a,Runnable b) throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var futures=List.of(executor.submit(()->{start.await();a.run();return null;}),executor.submit(()->{start.await();b.run();return null;}));
            start.countDown(); for(var future:futures) future.get(20,TimeUnit.SECONDS);
        }
    }
    @Test void simultaneousFirstAdjustmentsCreateOneStockAndAConsistentMovementChain() throws Exception {
        simultaneous(()->adjust(5),()->adjust(7));
        assertEquals(1,count("SELECT count(*) FROM stock WHERE id_farmacia=? AND id_presentacion=?",f.pharmacy(),f.presentation()));
        var rows=db.queryForList("SELECT cantidad_anterior,cantidad_nueva FROM movimiento_stock m JOIN stock s ON s.id_stock=m.id_stock WHERE s.id_farmacia=? ORDER BY id_movimiento",f.pharmacy());
        assertEquals(2,rows.size());assertEquals(0,rows.getFirst().get("cantidad_anterior"));
        assertEquals(rows.getFirst().get("cantidad_nueva"),rows.getLast().get("cantidad_anterior"));
        assertEquals(balance(),rows.getLast().get("cantidad_nueva"));
    }
    @Test void concurrentAdjustmentsMaterializeTheSameExpirationOnlyOnce() throws Exception {
        adjust(0);long reservation=reserve(T,f.presentation(),null);
        simultaneous(()->adjust(5),()->adjust(7));
        assertTrue(balance()==5 || balance()==7);
        assertEquals(1,count("SELECT count(*) FROM movimiento_stock WHERE id_reserva=? AND tipo='VENCIMIENTO'",reservation));
        assertEquals(3,count("SELECT count(*) FROM auditoria WHERE accion='AJUSTAR_STOCK' AND actor_cuenta_id=?",f.admin()));
    }
    @Test void adjustmentAndExistingUserDeactivationShareLocksAndDoNotDoubleRelease() throws Exception {
        adjust(0);long reservation=reserve(T,f.presentation(),null);
        simultaneous(()->adjust(5),()->administration.changeState(f.userAccount(),false,"Baja",f.adminActor(),null));
        assertEquals(5,balance());
        assertEquals(1,count("SELECT count(*) FROM movimiento_stock WHERE id_reserva=? AND tipo='VENCIMIENTO'",reservation));
    }
    @Autowired AdministrationService administration;
}
