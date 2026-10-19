package ar.edu.uade.medlink.presentacion;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.security.JwtTokens;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Catalog integration: executes against real PostgreSQL, never an in-memory substitute. */
@SpringBootTest(properties={"medlink.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.flyway.baseline-on-migrate=false"})
@AutoConfigureMockMvc
@EnabledIf("postgresDisponible")
class CatalogoPostgresTest {
    static PostgreSQLContainer container;
    static boolean postgresDisponible() {
        return System.getenv("MEDLINK_TEST_DB_URL")!=null || DockerClientFactory.instance().isDockerAvailable();
    }
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry properties) {
        String url=System.getenv("MEDLINK_TEST_DB_URL");
        if(url==null) {
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
    @AfterAll static void close() { if(container!=null) container.stop(); }
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired JwtTokens tokens;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper mapper;
    private String admin() {
        var tx=new TransactionTemplate(transactions);
        long id=tx.execute(status->{
            String email="catalogo-"+UUID.randomUUID()+"@test.local";
            long account=accounts.newAccount(email,"hash","ADMINISTRADOR",Instant.now());
            db.update("INSERT INTO administrador(id_cuenta,nombre,apellido) VALUES (?,'Admin','Catalogo')",account);
            return account;
        });
        return "Bearer "+tokens.create(accounts.byId(id).orElseThrow());
    }
    @Test void accentInsensitivePublicSearchAndAdministrativeRights() throws Exception {
        String bearer=admin();
        String medicine="Ácido acetilsalicílico "+UUID.randomUUID().toString().substring(0,8);
        mvc.perform(post("/api/admin/medicamentos").header("Authorization",bearer)
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\""+medicine+"\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("ACTIVO"));
        mvc.perform(get("/api/medicamentos").param("nombre",medicine.replace("Á","A").replace("í","i")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(post("/api/admin/medicamentos").contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"No autorizado\"}"))
            .andExpect(status().isUnauthorized());
        assertTrue(db.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_extension WHERE extname='unaccent')",Boolean.class));
    }
    @Test void activeDuplicateAndStateTransitionsDoNotErasePresentationOrAudit() throws Exception {
        String bearer=admin();
        String medicine=mvc.perform(post("/api/admin/medicamentos").header("Authorization",bearer)
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Producto de prueba\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long medId=mapper.readTree(medicine).get("id").asLong();
        String payload="{\"medicamentoId\":"+medId+",\"concentracionTexto\":\" 500   mg \","+
            "\"formaFarmaceutica\":\"comprimidos\",\"cantidadEnvase\":10,"+
            "\"unidadExpendio\":\"caja\",\"condicionDispensa\":\"VENTA_LIBRE\"}";
        String created=mvc.perform(post("/api/admin/presentaciones").header("Authorization",bearer)
            .contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.concentracionTexto").value("500 mg"))
            .andReturn().getResponse().getContentAsString();
        long presentId=mapper.readTree(created).get("id").asLong();
        mvc.perform(post("/api/admin/presentaciones").header("Authorization",bearer)
            .contentType(MediaType.APPLICATION_JSON).content(payload.replace("500   mg","500 mg")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PRESENTATION_DUPLICATE"));
        mvc.perform(post("/api/admin/presentaciones/"+presentId+"/desactivacion")
            .header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"motivo\":\"Cambio\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/presentaciones/"+presentId)).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/presentaciones/"+presentId+"/reactivacion")
            .header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"motivo\":\"Cambio\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/presentaciones/"+presentId)).andExpect(status().isOk());
        assertEquals(3,db.queryForObject("SELECT count(*) FROM auditoria WHERE tipo_entidad='PRESENTACION' AND entidad_id=?",Integer.class,presentId));
    }
}
