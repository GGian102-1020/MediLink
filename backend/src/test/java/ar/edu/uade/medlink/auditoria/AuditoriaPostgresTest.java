package ar.edu.uade.medlink.auditoria;

import ar.edu.uade.medlink.admin.*;
import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.*;
import ar.edu.uade.medlink.medicamento.dto.MedicamentoRequest;
import ar.edu.uade.medlink.medicamento.service.MedicamentoService;
import ar.edu.uade.medlink.security.*;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"medlink.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.flyway.baseline-on-migrate=false"})
@AutoConfigureMockMvc
@EnabledIf("postgresDisponible")
class AuditoriaPostgresTest {
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
    @AfterAll static void close() { if (container!=null) container.stop(); }
    @Autowired JdbcTemplate db;
    @Autowired AuditService audit;
    @Autowired AdministrationService administration;
    @Autowired MedicamentoService medicines;
    @Autowired AccountRepository accounts;
    @Autowired JwtTokens tokens;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    long admin,operator,userAccount,user,pharmacy,entity;
    final Instant t=Instant.parse("2026-10-20T18:30:00Z");
    TransactionTemplate tx() { return new TransactionTemplate(transactions); }
    long insert(String sql,Object... args) { return db.queryForObject(sql,Long.class,args); }
    long count(String sql,Object... args) { return db.queryForObject(sql,Long.class,args); }
    CurrentAccount actor() { return new CurrentAccount(admin,"ADMINISTRADOR","test",0); }
    String bearer() { return "Bearer "+tokens.create(accounts.byId(admin).orElseThrow()); }
    AuditoriaFiltro filter(Long related,Long account,String role,String action,String type,Long id,
                           OffsetDateTime from,OffsetDateTime until) {
        return new AuditoriaFiltro(related,account,role,action,type,id,from,until);
    }
    AuditoriaFiltro entityFilter() { return filter(null,null,null,null,"MEDICAMENTO",entity,null,null); }
    PageDTO<AuditoriaDTO> read(AuditoriaFiltro f) { return audit.listar(f,0,100,null,actor()); }
    long event(Long account,String role,String action,Long related,Instant time) {
        return tx().execute(status->insert("""
            INSERT INTO auditoria(fecha_hora,actor_cuenta_id,actor_rol,accion,tipo_entidad,entidad_id,
              usuario_relacionado_id,resumen_anterior,resumen_posterior)
            VALUES (?,?,?::actor_sistema,?::accion_auditoria,'MEDICAMENTO',?,?,?,?::jsonb)
            RETURNING id_auditoria
            """,Timestamp.from(time),account,role,action,entity,related,null,"{\"nombre\":\"Prueba\"}"));
    }
    @BeforeEach void fixture() {
        tx().executeWithoutResult(status->{
            String suffix=UUID.randomUUID().toString();
            pharmacy=insert("INSERT INTO farmacia(nombre,direccion) VALUES ('Prueba','Calle 1') RETURNING id_farmacia");
            admin=accounts.newAccount("admin-"+suffix+"@test.local","hash","ADMINISTRADOR",t);
            db.update("INSERT INTO administrador(id_cuenta,nombre,apellido) VALUES (?,'Admin','Auditoría')",admin);
            operator=accounts.newAccount("operator-"+suffix+"@test.local","hash","OPERADOR",t);
            db.update("INSERT INTO operador(id_cuenta,id_farmacia,nombre,apellido) VALUES (?,?,'Operador','Auditoría')",operator,pharmacy);
            userAccount=accounts.newAccount("user-"+suffix+"@test.local","hash","USUARIO",t);
            user=accounts.newUser(userAccount,"Usuario","Auditoría");
            entity=insert("INSERT INTO medicamento(nombre) VALUES ('Medicamento auditoría') RETURNING id_medicamento");
        });
    }
    @Test void administrativeCreationAndEditExposeBeforeAfterAndCorrelation() throws Exception {
        String correlation=UUID.randomUUID().toString();
        var request=new MockHttpServletRequest(); request.setAttribute("correlationId",correlation);
        var created=medicines.crear(new MedicamentoRequest("Original"),actor(),request);
        medicines.editar(created.id(),new MedicamentoRequest("Editado"),actor(),request);
        mvc.perform(get("/api/admin/auditoria").header("Authorization",bearer())
            .param("tipoEntidad","MEDICAMENTO").param("entidadId",Long.toString(created.id())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].rol").value("ADMINISTRADOR"))
            .andExpect(jsonPath("$.content[0].actorNombre").value("Admin Auditoría"))
            .andExpect(jsonPath("$.content[0].correlationId").value(correlation))
            .andExpect(jsonPath("$.content[0].resumenAnterior.nombre").value("Original"))
            .andExpect(jsonPath("$.content[0].resumenPosterior.nombre").value("Editado"))
            .andExpect(jsonPath("$.content[1].resumenPosterior.nombre").value("Original"));
    }
    @Test void individualAndCombinedFiltersFindExistingRecords() {
        long expected=event(admin,"ADMINISTRADOR","EDITAR_MEDICAMENTO",user,t);
        event(operator,"OPERADOR","CREAR_MEDICAMENTO",null,t.plusSeconds(1));
        List<AuditoriaFiltro> filters=List.of(filter(user,null,null,null,null,null,null,null),
            filter(null,admin,null,null,null,null,null,null),filter(null,null,"ADMINISTRADOR",null,"MEDICAMENTO",entity,null,null),
            filter(null,null,null,"EDITAR_MEDICAMENTO","MEDICAMENTO",entity,null,null),
            filter(user,admin,"ADMINISTRADOR","EDITAR_MEDICAMENTO","MEDICAMENTO",entity,
                t.atOffset(ZoneOffset.UTC),t.plusSeconds(1).atOffset(ZoneOffset.UTC)));
        for (var f:filters) assertEquals(List.of(expected),read(f).content().stream().map(AuditoriaDTO::id).toList());
        assertEquals(2,read(entityFilter()).totalElements());
        assertTrue(read(filter(null,null,null,null,"MEDICAMENTO",Long.MAX_VALUE,null,null)).content().isEmpty());
    }
    @Test void relatedUserNeverMeansActorAccount() {
        long related=event(admin,"ADMINISTRADOR","EDITAR_MEDICAMENTO",user,t);
        event(userAccount,"USUARIO","EDITAR_MEDICAMENTO",null,t);
        long system=event(null,"SISTEMA","EDITAR_MEDICAMENTO",user,t);
        assertEquals(List.of(system,related),read(filter(user,null,null,null,null,null,null,null))
            .content().stream().map(AuditoriaDTO::id).toList());
        assertEquals(1,read(filter(null,userAccount,null,null,null,null,null,null)).totalElements());
    }
    @Test void everyScalarFilterCanBeUsedIndependently() {
        long id=event(admin,"ADMINISTRADOR","EDITAR_MEDICAMENTO",user,t);
        Map<AuditoriaFiltro,java.util.function.Predicate<AuditoriaDTO>> cases=new LinkedHashMap<>();
        cases.put(filter(user,null,null,null,null,null,null,null),r->Objects.equals(user,r.usuarioRelacionadoId()));
        cases.put(filter(null,admin,null,null,null,null,null,null),r->Objects.equals(admin,r.actorCuentaId()));
        cases.put(filter(null,null,"ADMINISTRADOR",null,null,null,null,null),r->r.rol().equals("ADMINISTRADOR"));
        cases.put(filter(null,null,null,"EDITAR_MEDICAMENTO",null,null,null,null),r->r.accion().equals("EDITAR_MEDICAMENTO"));
        cases.put(filter(null,null,null,null,"MEDICAMENTO",null,null,null),r->r.tipoEntidad().equals("MEDICAMENTO"));
        cases.put(filter(null,null,null,null,null,entity,null,null),r->r.entidadId()==entity);
        for (var entry:cases.entrySet()) {
            var rows=read(entry.getKey()).content();
            assertTrue(rows.stream().anyMatch(r->r.id()==id));
            assertTrue(rows.stream().allMatch(entry.getValue()));
        }
    }
    @Test void temporalRangeIsInclusiveThenExclusiveWithOffsets() {
        long first=event(admin,"ADMINISTRADOR","EDITAR_MEDICAMENTO",null,t);
        long second=event(admin,"ADMINISTRADOR","EDITAR_MEDICAMENTO",null,t.plusMillis(1));
        event(admin,"ADMINISTRADOR","EDITAR_MEDICAMENTO",null,t.plusMillis(2));
        assertEquals(List.of(second,first),read(filter(null,null,null,null,"MEDICAMENTO",entity,
            t.atOffset(ZoneOffset.ofHours(-3)),t.plusMillis(2).atOffset(ZoneOffset.UTC)))
            .content().stream().map(AuditoriaDTO::id).toList());
        assertEquals(2,read(filter(null,null,null,null,"MEDICAMENTO",entity,null,t.plusMillis(2).atOffset(ZoneOffset.UTC))).totalElements());
        assertEquals(1,read(filter(null,null,null,null,"MEDICAMENTO",entity,t.plusMillis(2).atOffset(ZoneOffset.UTC),null)).totalElements());
    }
    @Test void stableDefaultAndExplicitSortAndPagination() {
        long first=event(admin,"ADMINISTRADOR","EDITAR_MEDICAMENTO",null,t);
        long second=event(admin,"ADMINISTRADOR","EDITAR_MEDICAMENTO",null,t);
        long third=event(admin,"ADMINISTRADOR","EDITAR_MEDICAMENTO",null,t.plusSeconds(1));
        var page=audit.listar(entityFilter(),1,1,null,actor());
        assertEquals(second,page.content().getFirst().id()); assertEquals(3,page.totalElements());
        assertEquals(3,page.totalPages()); assertFalse(page.first()); assertFalse(page.last());
        assertEquals(List.of(first,second,third),audit.listar(entityFilter(),0,20,
            List.of("fechaHora,asc","id,asc"),actor()).content().stream().map(AuditoriaDTO::id).toList());
        assertEquals(List.of(third,second,first),audit.listar(entityFilter(),0,20,
            List.of("id,desc"),actor()).content().stream().map(AuditoriaDTO::id).toList());
        assertTrue(audit.listar(entityFilter(),3,1,null,actor()).content().isEmpty());
    }
    @Test void systemAndHumanActorsResolveWithoutExposingAccounts() {
        event(null,"SISTEMA","CREAR_MEDICAMENTO",null,t);
        event(operator,"OPERADOR","EDITAR_MEDICAMENTO",null,t);
        event(userAccount,"USUARIO","EDITAR_MEDICAMENTO",null,t);
        var rows=read(entityFilter()).content();
        assertEquals("Usuario Auditoría",rows.get(0).actorNombre());
        assertEquals("Operador Auditoría",rows.get(1).actorNombre());
        assertNull(rows.get(2).actorCuentaId()); assertNull(rows.get(2).actorNombre());
        assertEquals("SISTEMA",rows.get(2).rol());
    }
    @Test void updateAndDeleteAreBlockedByDatabaseTriggers() {
        long id=event(admin,"ADMINISTRADOR","EDITAR_MEDICAMENTO",null,t);
        assertThrows(DataAccessException.class,()->db.update("UPDATE auditoria SET motivo='Cambio' WHERE id_auditoria=?",id));
        assertThrows(DataAccessException.class,()->db.update("DELETE FROM auditoria WHERE id_auditoria=?",id));
        assertEquals(1,count("SELECT count(*) FROM auditoria WHERE id_auditoria=?",id));
    }
    @Test void businessRollbackAlsoRollsBackItsAudit() {
        long before=count("SELECT count(*) FROM auditoria WHERE actor_cuenta_id=?",admin);
        String name="Rollback "+UUID.randomUUID();
        assertThrows(IllegalStateException.class,()->tx().executeWithoutResult(status->{
            medicines.crear(new MedicamentoRequest(name),actor(),null);
            throw new IllegalStateException("Forzar rollback");
        }));
        assertEquals(0,count("SELECT count(*) FROM medicamento WHERE nombre=?",name));
        assertEquals(before,count("SELECT count(*) FROM auditoria WHERE actor_cuenta_id=?",admin));
    }
    @Test void auditFailureRollsBackBusinessAndRequiresATransaction() {
        String name="Audit failure "+UUID.randomUUID();
        assertThrows(DataAccessException.class,()->tx().executeWithoutResult(status->{
            long id=insert("INSERT INTO medicamento(nombre) VALUES (?) RETURNING id_medicamento",name);
            audit.record(Long.MAX_VALUE,"ADMINISTRADOR","CREAR_MEDICAMENTO","MEDICAMENTO",id,null,
                null,null,Map.of("nombre",name),null);
        }));
        assertEquals(0,count("SELECT count(*) FROM medicamento WHERE nombre=?",name));
        assertThrows(IllegalTransactionStateException.class,()->audit.record(admin,"ADMINISTRADOR",
            "CREAR_MEDICAMENTO","MEDICAMENTO",entity,null,null,null,Map.of("nombre","Fuera de tx"),null));
    }
    @Test void secretsAreNeitherPersistedNorExposedFromLegacySummaries() throws Exception {
        tx().executeWithoutResult(status->audit.record(admin,"ADMINISTRADOR","EDITAR_MEDICAMENTO",
            "MEDICAMENTO",entity,null,null,Map.of("passwordHash","SECRET"),
            Map.of("nombre","Seguro","jwt","SECRET","estado",Map.of("token","SECRET")),null));
        assertFalse(db.queryForObject("SELECT resumen_posterior::text FROM auditoria WHERE entidad_id=? AND tipo_entidad='MEDICAMENTO'",
            String.class,entity).contains("SECRET"));
        db.update("INSERT INTO auditoria(fecha_hora,actor_rol,accion,tipo_entidad,entidad_id,resumen_posterior) " +
            "VALUES (?,'SISTEMA','CREAR_MEDICAMENTO','MEDICAMENTO',?,?::jsonb)",Timestamp.from(t),entity,
            "{\"passwordHash\":\"SECRET\",\"jwt\":\"SECRET\",\"nombre\":\"Legado\",\"cantidad_disponible\":15,\"id_farmacia\":1,\"causa\":\"OTRA_CAUSA\"}");
        String body=mvc.perform(get("/api/admin/auditoria").header("Authorization",bearer())
            .param("tipoEntidad","MEDICAMENTO").param("entidadId",Long.toString(entity)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (String forbidden:List.of("SECRET","passwordHash","jwt","jti","correo")) assertFalse(body.contains(forbidden));
        assertTrue(body.contains("cantidad_disponible")); assertTrue(body.contains("id_farmacia"));
        assertTrue(body.contains("OTRA_CAUSA"));
    }
    @Test void accountAdministrationRecordsSubjectAndOperatorChanges() {
        administration.changeState(userAccount,false,"Prueba administrativa",actor(),null);
        var row=read(filter(user,null,null,"DESHABILITAR_CUENTA",null,null,null,null)).content().getFirst();
        assertEquals(admin,row.actorCuentaId()); assertEquals(userAccount,row.entidadId());
        assertEquals("ACTIVA",row.resumenAnterior().get("estado"));
        assertEquals("DESHABILITADA",row.resumenPosterior().get("estado"));
        var created=administration.createOperator(new AdminDtos.OperadorCreateRequest("Nuevo","Operador",
            "nuevo-"+UUID.randomUUID()+"@test.local","Password123!",pharmacy),actor(),null);
        administration.updateOperator(created.operadorId(),new AdminDtos.OperadorUpdateRequest("Editado","Operador"),actor(),null);
        var changes=read(filter(null,null,null,null,"OPERADOR",created.operadorId(),null,null)).content();
        assertEquals(2,changes.size()); assertEquals("Nuevo",changes.getFirst().resumenAnterior().get("nombre"));
    }
    @Test void realJwtAndCorrelationFlowReachAuditAndErrors() throws Exception {
        String correlation=UUID.randomUUID().toString();
        String body=mvc.perform(post("/api/admin/medicamentos").header("Authorization",bearer())
            .header("X-Correlation-Id",correlation).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Correlacionado\"}"))
            .andExpect(status().isCreated()).andExpect(header().string("X-Correlation-Id",correlation))
            .andReturn().getResponse().getContentAsString();
        long id=mapper.readTree(body).get("id").asLong();
        assertEquals(correlation,read(filter(null,null,null,null,"MEDICAMENTO",id,null,null))
            .content().getFirst().correlationId());
        for (long account:List.of(operator,userAccount))
            mvc.perform(get("/api/admin/auditoria").header("Authorization","Bearer "+tokens.create(accounts.byId(account).orElseThrow())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("FORBIDDEN"));
        mvc.perform(get("/api/admin/auditoria").header("Authorization","Bearer invalid").header("X-Correlation-Id",correlation))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.correlationId").value(correlation));
        mvc.perform(get("/api/admin/auditoria").header("Authorization",bearer()).param("size","101")
            .header("X-Correlation-Id",correlation)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.correlationId").value(correlation));
        mvc.perform(get("/api/admin/auditoria").header("Authorization",bearer())
            .param("desde","2026-10-20T15:30:00.000-03:00").param("hasta","2026-10-20T18:30:00.000Z"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/admin/auditoria").header("Authorization",bearer()).param("sort","accion,asc"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDATION_ERROR"));
    }
    @Test void flywayFreshInstallHasAllVersionsAndAuditIndex() {
        assertEquals(3,count("SELECT count(*) FROM flyway_schema_history WHERE success AND version IN ('1','2','3')"));
        assertEquals(1,count("SELECT count(*) FROM pg_indexes WHERE tablename='auditoria' AND indexname='ix_auditoria_fecha'"));
    }
    @Test void explicitBaselineAdoptsSeededSchemaWithoutOverwritingData() throws Exception {
        // This method alone creates/drops a uniquely named disposable database.
        String name="medlink_adoption_"+UUID.randomUUID().toString().replace("-","");
        String url,username;
        try (var connection=db.getDataSource().getConnection()) {
            url=connection.getMetaData().getURL(); username=connection.getMetaData().getUserName();
        }
        String isolatedUrl=url.replaceFirst("/[^/?]+(?=\\?|$)","/"+name);
        String password=container==null?System.getenv().getOrDefault("MEDLINK_TEST_DB_PASSWORD",""):container.getPassword();
        db.execute("CREATE DATABASE "+name);
        try {
            var isolated=new DriverManagerDataSource(isolatedUrl,username,password);
            var jdbc=new JdbcTemplate(isolated);
            jdbc.execute(Files.readString(Path.of("../database/01_medlink_esquema.sql")));
            jdbc.execute(Files.readString(Path.of("../database/02_datos_prueba.sql")));
            long before=jdbc.queryForObject("SELECT count(*) FROM auditoria",Long.class);
            assertTrue(before>0);
            assertThrows(FlywayException.class,()->Flyway.configure().dataSource(isolated).load().migrate());
            Flyway.configure().dataSource(isolated).baselineOnMigrate(true).baselineVersion("1").load().migrate();
            assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM auditoria",Long.class));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE type='BASELINE' AND version='1'",Integer.class));
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE type='SQL' AND success AND version IN ('2','3')",Integer.class));
        } finally { db.execute("DROP DATABASE "+name+" WITH (FORCE)"); }
    }
}
