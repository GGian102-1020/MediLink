package ar.edu.uade.medlink.suspension;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.security.CurrentAccount;
import ar.edu.uade.medlink.suspension.service.SuspensionService;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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

/** End-to-end JPA mapping, filters and immutable interval schema on PostgreSQL 16. */
@SpringBootTest(properties={"medlink.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.flyway.baseline-on-migrate=false"})
@EnabledIf("postgresDisponible")
class SuspensionPostgresTest {
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
    @AfterAll static void close() { if(container!=null) container.stop(); }

    @Autowired JdbcTemplate db;
    @Autowired AccountRepository accounts;
    @Autowired PlatformTransactionManager transactions;
    @Autowired SuspensionService service;
    @Autowired Clock clock;
    private static final CurrentAccount ADMIN=new CurrentAccount(1,"ADMINISTRADOR","t",0);

    private long user() {
        return new TransactionTemplate(transactions).execute(status->{
            String email="suspension-"+UUID.randomUUID()+"@test.local";
            long account=accounts.newAccount(email,"hash","USUARIO",clock.instant());
            return db.queryForObject("INSERT INTO usuario(id_cuenta,nombre,apellido) VALUES (?,'Prueba','Suspension') RETURNING id_usuario",
                Long.class,account);
        });
    }
    private long insert(long user,Instant from) {
        return db.queryForObject("INSERT INTO suspension (id_usuario,fecha_hora_inicio,fecha_hora_fin,motivo) " +
            "VALUES (?, ?, ?, 'INCUMPLIMIENTOS_REITERADOS') RETURNING id_suspension",Long.class,
            user,java.sql.Timestamp.from(from),java.sql.Timestamp.from(from.plus(7,ChronoUnit.DAYS)));
    }

    @Test void jpaHistoryReadsNativeEnumAndFiltersByEffectiveTimeAndStartRange() {
        long id=user();
        Instant now=clock.instant().truncatedTo(ChronoUnit.MILLIS);
        insert(id,now.minus(15,ChronoUnit.DAYS));
        long current=insert(id,now.minus(1,ChronoUnit.HOURS));
        var complete=service.listar(id,null,null,null,0,20,null,ADMIN);
        assertEquals(2,complete.totalElements());
        assertEquals(current,complete.content().getFirst().id());
        assertTrue(complete.content().getFirst().vigente());
        assertFalse(complete.content().getLast().vigente());
        assertEquals("INCUMPLIMIENTOS_REITERADOS",complete.content().getFirst().motivo());
        assertEquals(1,service.listar(id,true,null,null,0,20,null,ADMIN).totalElements());
        assertEquals(1,service.listar(id,false,null,null,0,20,null,ADMIN).totalElements());
        var start=now.minus(2,ChronoUnit.HOURS).atOffset(ZoneOffset.UTC);
        var end=now.minus(1,ChronoUnit.HOURS).atOffset(ZoneOffset.UTC);
        assertEquals(0,service.listar(id,null,start,end,0,20,null,ADMIN).totalElements());
        assertEquals(1,service.listar(id,null,start,end.plusNanos(1_000_000),0,20,null,ADMIN).totalElements());
    }

    @Test void schemaForbidsChangingOrOverlappingHistoricalIntervals() {
        long id=user();
        Instant from=clock.instant().truncatedTo(ChronoUnit.MILLIS).minus(1,ChronoUnit.DAYS);
        long row=insert(id,from);
        assertThrows(Exception.class,()->db.update("UPDATE suspension SET fecha_hora_fin=fecha_hora_fin + interval '1 day' " +
            "WHERE id_suspension=?",row));
        assertThrows(Exception.class,()->insert(id,from.plus(1,ChronoUnit.DAYS)));
        assertEquals(1,service.listar(id,null,null,null,0,20,null,ADMIN).totalElements());
    }
}
