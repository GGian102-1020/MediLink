package ar.edu.uade.medlink.prescripcion;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.prescripcion.dto.PrescripcionCreateRequest;
import ar.edu.uade.medlink.prescripcion.service.*;
import ar.edu.uade.medlink.security.CurrentAccount;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
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

/** RN-13 under contention: simultaneous reservations never commit more units than the prescription authorizes. */
@SpringBootTest(properties={"medlink.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.flyway.baseline-on-migrate=false"})
@EnabledIf("postgresDisponible")
class PrescripcionConcurrencyTest {
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

    record Fixture(CurrentAccount admin, long user, long presentation) { }
    private Fixture fixture() {
        return new TransactionTemplate(transactions).execute(status->{
            String suffix=UUID.randomUUID().toString();
            long adminAccount=accounts.newAccount("rx-cc-admin-"+suffix+"@test.local","hash","ADMINISTRADOR",Instant.now());
            db.update("INSERT INTO administrador(id_cuenta,nombre,apellido) VALUES (?,'Admin','Rx')",adminAccount);
            long patientAccount=accounts.newAccount("rx-cc-user-"+suffix+"@test.local","hash","USUARIO",Instant.now());
            long user=db.queryForObject("INSERT INTO usuario(id_cuenta,nombre,apellido) VALUES (?,'Paciente','Rx') RETURNING id_usuario",
                Long.class,patientAccount);
            long med=db.queryForObject("INSERT INTO medicamento(nombre) VALUES ('Amoxicilina') RETURNING id_medicamento",Long.class);
            long rx=db.queryForObject("INSERT INTO presentacion_medicamento(id_medicamento,concentracion_texto,forma_farmaceutica," +
                "cantidad_envase,unidad_expendio,condicion_dispensa) VALUES (?,?,'Comprimidos',16,'Caja','BAJO_RECETA') RETURNING id_presentacion",
                Long.class,med,"500 mg "+suffix.substring(0,8));
            return new Fixture(new CurrentAccount(adminAccount,"ADMINISTRADOR","t",0),user,rx);
        });
    }

    /** Runs {@code attempts} commitments of one unit at the same instant and returns each outcome. */
    private List<String> commitConcurrently(Fixture f, String code, int attempts) throws Exception {
        var tx=new TransactionTemplate(transactions);
        var start=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(attempts);
        try {
            var results=new ArrayList<Future<String>>();
            for(int i=0;i<attempts;i++) results.add(pool.submit(()->{
                start.await();
                try {
                    tx.executeWithoutResult(status->{
                        validation.validarYComprometer(f.user(),code,f.presentation(),1,Instant.now());
                        // Keep the row locks a moment so the other attempts really queue behind them.
                        try { Thread.sleep(50); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
                    });
                    return "OK";
                } catch(ApiException e) { return e.codigo(); }
            }));
            start.countDown();
            var outcomes=new ArrayList<String>();
            for(var r:results) outcomes.add(r.get(30,TimeUnit.SECONDS));
            return outcomes;
        } finally { pool.shutdownNow(); }
    }

    private long create(Fixture f, String code, int authorized) {
        LocalDate today=LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        var req=new PrescripcionCreateRequest(code,f.user(),today,today.plusDays(10),
            List.of(new PrescripcionCreateRequest.Item(f.presentation(),authorized)));
        return service.crear(req,f.admin(),null).items().getFirst().id();
    }
    private int committed(long itemId) {
        return db.queryForObject("SELECT cantidad_comprometida FROM item_prescripcion WHERE id_item_prescripcion=?",Integer.class,itemId);
    }

    @Test void onlyOneOfTwoSimultaneousReservationsGetsTheLastUnit() throws Exception {
        var f=fixture();
        String code="RX-CC-"+UUID.randomUUID();
        long itemId=create(f,code,1);
        var outcomes=commitConcurrently(f,code,2);
        assertEquals(1,Collections.frequency(outcomes,"OK"),outcomes.toString());
        assertEquals(1,Collections.frequency(outcomes,"PRESCRIPTION_QUANTITY_INSUFFICIENT"),outcomes.toString());
        assertEquals(1,committed(itemId));
    }

    @Test void manySimultaneousReservationsNeverExceedTheAuthorizedQuantity() throws Exception {
        var f=fixture();
        String code="RX-CC-"+UUID.randomUUID();
        long itemId=create(f,code,3);
        var outcomes=commitConcurrently(f,code,6);
        assertEquals(3,Collections.frequency(outcomes,"OK"),outcomes.toString());
        assertEquals(3,Collections.frequency(outcomes,"PRESCRIPTION_QUANTITY_INSUFFICIENT"),outcomes.toString());
        assertEquals(3,committed(itemId));
    }
}
