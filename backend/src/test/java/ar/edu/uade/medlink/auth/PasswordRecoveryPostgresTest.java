package ar.edu.uade.medlink.auth;

import ar.edu.uade.medlink.common.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"medlink.jwt.secret-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.flyway.baseline-on-migrate=false", "medlink.reservas.scheduler.enabled=false"})
@AutoConfigureMockMvc
@EnabledIf("postgresDisponible")
class PasswordRecoveryPostgresTest {
    static PostgreSQLContainer container;
    static boolean postgresDisponible() { return DockerClientFactory.instance().isDockerAvailable(); }
    @DynamicPropertySource static void postgres(DynamicPropertyRegistry registry) {
        container=new PostgreSQLContainer("postgres:16-alpine"); container.start();
        registry.add("spring.datasource.url",container::getJdbcUrl);
        registry.add("spring.datasource.username",container::getUsername);
        registry.add("spring.datasource.password",container::getPassword);
    }
    @AfterAll static void close() { if (container!=null) container.stop(); }
    @Autowired PasswordRecoveryService recovery;
    @Autowired AuthService auth;
    @Autowired AccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @MockitoBean RecoveryMailer mail;
    @MockitoBean Clock clock;
    long accountId;
    String email;
    MockHttpServletRequest request;
    final Instant now=Instant.parse("2026-11-23T16:00:00Z");

    @BeforeEach void setup() {
        reset(mail,clock); when(clock.instant()).thenReturn(now); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(mail.configured()).thenReturn(true);
        email="recover-"+java.util.UUID.randomUUID()+"@demo.test";
        new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(status -> {
            accountId=accounts.newAccount(email,encoder.encode("Password123!"),"USUARIO",now);
            accounts.newUser(accountId,"Demo","Recuperación");
        });
        request=new MockHttpServletRequest(); request.setRemoteAddr(java.util.UUID.randomUUID().toString());
    }
    String issue() {
        recovery.request(email,request);
        var code=ArgumentCaptor.forClass(String.class); verify(mail).send(eq(email),code.capture());
        return code.getValue();
    }
    @Test void resetIsSingleUseAndInvalidatesExistingSessions() throws Exception {
        var login=auth.login(new AuthDtos.LoginRequest(email,"Password123!"));
        String code=issue(); assertTrue(code.matches("[0-9]{8}"));
        String stored=db.queryForObject("SELECT codigo_hash FROM recuperacion_contrasena WHERE id_cuenta=?",String.class,accountId);
        assertEquals(64,stored.length()); assertNotEquals(code,stored);
        recovery.reset(email.toUpperCase(),code,"NewPassword123!",request);
        assertEquals(1,accounts.byId(accountId).orElseThrow().tokenVersion());
        assertNotNull(auth.login(new AuthDtos.LoginRequest(email,"NewPassword123!")));
        assertThrows(ApiException.class,()->auth.login(new AuthDtos.LoginRequest(email,"Password123!")));
        assertThrows(ApiException.class,()->recovery.reset(email,code,"OtherPassword123!",request));
        mvc.perform(get("/api/me").header("Authorization","Bearer "+login.accessToken())).andExpect(status().isUnauthorized());
    }
    @Test void fifthWrongAttemptLocksTheCodeAndPersistsFailures() {
        String code=issue(); String wrong=code.equals("00000000") ? "11111111" : "00000000";
        for(int i=0;i<5;i++) assertThrows(ApiException.class,()->recovery.reset(email,wrong,"NewPassword123!",request));
        assertEquals(5,db.queryForObject("SELECT intentos FROM recuperacion_contrasena WHERE id_cuenta=?",Integer.class,accountId));
        assertThrows(ApiException.class,()->recovery.reset(email,code,"NewPassword123!",request));
        assertEquals(0,accounts.byId(accountId).orElseThrow().tokenVersion());
    }
    @Test void expirationAndPasswordChangesInvalidateTheChallenge() {
        String code=issue(); when(clock.instant()).thenReturn(now.plusSeconds(900));
        assertThrows(ApiException.class,()->recovery.reset(email,code,"NewPassword123!",request));
        when(clock.instant()).thenReturn(now); accounts.updatePassword(accountId,encoder.encode("OtherPassword123!"));
        assertThrows(ApiException.class,()->recovery.reset(email,code,"NewPassword123!",request));
    }
    @Test void unknownDisabledAndKnownEmailsHaveSamePublicResponse() throws Exception {
        for(String address:new String[]{email,"missing@demo.test"}) {
            mvc.perform(post("/api/auth/password-recovery").contentType("application/json")
                .content("{\"correo\":\""+address+"\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.mensaje").value(
                    "Si el correo corresponde a una cuenta activa, recibirás un código. Revisá también el correo no deseado."));
        }
        accounts.changeState(accountId,"DESHABILITADA",true);
        recovery.request(email,request);
        verify(mail,times(1)).send(eq(email),anyString());
    }
    @Test void cooldownAndRateLimitPreventMailFlooding() {
        issue(); recovery.request(email,request); verify(mail,times(1)).send(eq(email),anyString());
        for(int i=0;i<8;i++) recovery.request("missing@demo.test",request);
        assertEquals("RECOVERY_LIMIT",assertThrows(ApiException.class,
            ()->recovery.request(email,request)).codigo());
    }
    @Test void mailFailureDoesNotLeaveAnUnusableChallenge() {
        doThrow(new org.springframework.mail.MailSendException("unavailable")).when(mail).send(eq(email),anyString());
        assertEquals("RECOVERY_UNAVAILABLE",assertThrows(ApiException.class,()->recovery.request(email,request)).codigo());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM recuperacion_contrasena WHERE id_cuenta=?",Integer.class,accountId));
    }
}
