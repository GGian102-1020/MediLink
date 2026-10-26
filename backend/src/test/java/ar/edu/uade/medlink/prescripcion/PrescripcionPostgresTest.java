package ar.edu.uade.medlink.prescripcion;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.prescripcion.dto.PrescripcionCreateRequest;
import ar.edu.uade.medlink.prescripcion.repository.ItemPrescripcionRepository;
import ar.edu.uade.medlink.prescripcion.service.*;
import ar.edu.uade.medlink.security.CurrentAccount;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Prescription mapping, triggers and audit against real PostgreSQL, never an in-memory substitute. */
@SpringBootTest(properties={"medlink.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.flyway.baseline-on-migrate=false"})
@EnabledIf("postgresDisponible")
class PrescripcionPostgresTest {
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
    @Autowired AccountRepository accounts;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PrescripcionService service;
    @Autowired PrescriptionValidationService validation;
    @Autowired ItemPrescripcionRepository items;

    record Fixture(CurrentAccount admin, CurrentAccount patient, long user, long rxPresentation, long otcPresentation) { }
    private Fixture fixture() {
        return new TransactionTemplate(transactions).execute(status->{
            String suffix=UUID.randomUUID().toString();
            long adminAccount=accounts.newAccount("rx-admin-"+suffix+"@test.local","hash","ADMINISTRADOR",Instant.now());
            db.update("INSERT INTO administrador(id_cuenta,nombre,apellido) VALUES (?,'Admin','Rx')",adminAccount);
            long patientAccount=accounts.newAccount("rx-user-"+suffix+"@test.local","hash","USUARIO",Instant.now());
            long user=db.queryForObject("INSERT INTO usuario(id_cuenta,nombre,apellido) VALUES (?,'Paciente','Rx') RETURNING id_usuario",
                Long.class,patientAccount);
            long med=db.queryForObject("INSERT INTO medicamento(nombre) VALUES ('Amoxicilina') RETURNING id_medicamento",Long.class);
            String structure=suffix.substring(0,8);
            long rx=db.queryForObject("INSERT INTO presentacion_medicamento(id_medicamento,concentracion_texto,forma_farmaceutica," +
                "cantidad_envase,unidad_expendio,condicion_dispensa) VALUES (?,?,'Comprimidos',16,'Caja','BAJO_RECETA') RETURNING id_presentacion",
                Long.class,med,"500 mg "+structure);
            long otc=db.queryForObject("INSERT INTO presentacion_medicamento(id_medicamento,concentracion_texto,forma_farmaceutica," +
                "cantidad_envase,unidad_expendio,condicion_dispensa) VALUES (?,?,'Comprimidos',16,'Caja','VENTA_LIBRE') RETURNING id_presentacion",
                Long.class,med,"250 mg "+structure);
            return new Fixture(new CurrentAccount(adminAccount,"ADMINISTRADOR","t",0),
                new CurrentAccount(patientAccount,"USUARIO","t",0),user,rx,otc);
        });
    }
    private PrescripcionCreateRequest request(String code, Fixture f, LocalDate desde, LocalDate hasta, long presentation, int quantity) {
        return new PrescripcionCreateRequest(code,f.user(),desde,hasta,List.of(new PrescripcionCreateRequest.Item(presentation,quantity)));
    }

    @Test void createsListsAndAnnulsWithAudit() {
        var f=fixture();
        String code="RX-IT-"+UUID.randomUUID();
        LocalDate today=LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        var created=service.crear(request(code,f,today.minusDays(1),today.plusDays(10),f.rxPresentation(),2),f.admin(),null);
        assertEquals("ACTIVA",created.estado().name());
        assertEquals(2,created.items().getFirst().cantidadDisponible());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM item_prescripcion WHERE id_prescripcion=?",Integer.class,created.id()));

        assertEquals("PRESCRIPTION_CODE_ALREADY_EXISTS",assertThrows(ApiException.class,
            ()->service.crear(request(code,f,today,today,f.rxPresentation(),1),f.admin(),null)).codigo());
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->service.crear(request(code+"-otc",f,today,today,f.otcPresentation(),1),f.admin(),null)).codigo());

        assertEquals(1,service.listarAdmin(f.user(),null,code,0,20,null,f.admin()).totalElements());
        assertEquals(created.id(),service.detalleAdmin(created.id(),f.admin()).id());

        var annulled=service.anular(created.id(),"Emitida con error",f.admin(),null);
        assertEquals("ANULADA",annulled.estado().name());
        assertEquals("ANULADA",db.queryForObject("SELECT estado::text FROM prescripcion WHERE id_prescripcion=?",String.class,created.id()));
        assertEquals(2,db.queryForObject("SELECT count(*) FROM auditoria WHERE tipo_entidad='PRESCRIPCION' AND entidad_id=? " +
            "AND usuario_relacionado_id=?",Integer.class,created.id(),f.user()));
        // The schema keeps an annulled prescription annulled even through direct SQL.
        assertThrows(Exception.class,()->db.update("UPDATE prescripcion SET estado='ACTIVA' WHERE id_prescripcion=?",created.id()));
    }

    @Test void validationCommitsWithinTheCallersTransaction() {
        var f=fixture();
        String code="RX-IT-"+UUID.randomUUID();
        LocalDate today=LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        var created=service.crear(request(code,f,today,today,f.rxPresentation(),1),f.admin(),null);
        long itemId=created.items().getFirst().id();
        var tx=new TransactionTemplate(transactions);

        tx.executeWithoutResult(status->validation.validarYComprometer(f.user(),code,f.rxPresentation(),1,Instant.now()));
        assertEquals(1,db.queryForObject("SELECT cantidad_comprometida FROM item_prescripcion WHERE id_item_prescripcion=?",Integer.class,itemId));
        // RN-13: the single authorized unit is already committed.
        assertEquals("PRESCRIPTION_QUANTITY_INSUFFICIENT",assertThrows(ApiException.class,
            ()->tx.executeWithoutResult(status->validation.validarYComprometer(f.user(),code,f.rxPresentation(),1,Instant.now()))).codigo());
        assertEquals("PRESCRIPTION_INVALID",assertThrows(ApiException.class,
            ()->tx.executeWithoutResult(status->validation.validarYComprometer(f.user()+100000,code,f.rxPresentation(),1,Instant.now()))).codigo());
        // Must never run outside a transaction: the row lock would be released immediately.
        assertThrows(Exception.class,()->validation.validarYComprometer(f.user(),code,f.rxPresentation(),1,Instant.now()));

        tx.executeWithoutResult(status->items.findById(itemId).orElseThrow().utilizar(1));
        assertEquals(List.of(0,1),db.queryForObject("SELECT ARRAY[cantidad_comprometida,cantidad_utilizada] FROM item_prescripcion " +
            "WHERE id_item_prescripcion=?",(rs,n)->Arrays.asList((Integer[])rs.getArray(1).getArray()),itemId));
    }
}
