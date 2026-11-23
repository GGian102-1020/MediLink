package ar.edu.uade.medlink.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import ar.edu.uade.medlink.auth.AuthDtos.*;
import ar.edu.uade.medlink.auth.AccountRepository.Account;
import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import ar.edu.uade.medlink.security.JwtTokens;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.servlet.http.HttpServletRequest;

class AuthServiceTest {
    AccountRepository accounts=mock(AccountRepository.class);
    PasswordEncoder encoder=mock(PasswordEncoder.class);
    JwtTokens tokens=mock(JwtTokens.class);
    AuditService audit=mock(AuditService.class);
    AuthService service;
    @BeforeEach void before() { service=new AuthService(accounts,encoder,tokens,audit,Clock.systemUTC()); }

    @Test void meRetrievesSuspensionThroughAccountRepositoryWithoutExposingJdbc() {
        var actor = new CurrentAccount(21, "USUARIO", "jti", 0);
        var profile = new CuentaPerfilDTO(21, 7, "USUARIO", "ana@t.test", "ACTIVA", "Ana", "Paz", null);
        when(accounts.perfil(21)).thenReturn(profile);
        when(accounts.activeSuspension(eq(7L), any())).thenReturn(Optional.empty());
        var result = service.me(actor);
        assertEquals(profile, result.perfil());
        assertNull(result.suspensionVigente());
        verify(accounts).activeSuspension(eq(7L), any());
    }

    @Test void registrationNormalizesEmailAndCreatesOnlyUsuarioWithAtomicServiceContract() {
        when(accounts.byEmail("User@Example.test")).thenReturn(Optional.empty());
        when(encoder.encode("Password123!")).thenReturn("$2a$valid");
        when(accounts.newAccount(eq("User@Example.test"),eq("$2a$valid"),eq("USUARIO"),any())).thenReturn(21L);
        when(accounts.userProfile(21L)).thenReturn(new UsuarioPerfilDTO(7,21,"Ana","Paz","User@Example.test","ACTIVA","USUARIO"));
        var response=service.register(new RegisterRequest(" Ana ","Paz "," User@Example.test ","Password123!",
            "30.123.456","1990-05-10"," 11 4444-5555 ","OSDE"," 123 "));
        assertEquals(7,response.usuarioId());
        verify(accounts).newUser(21,"Ana","Paz",new DatosPersonales("30123456",LocalDate.of(1990,5,10),
            "11 4444-5555","OSDE","123"));
    }
    @Test void duplicateEmailRejectsCreation() {
        when(accounts.byEmail("x@a.test")).thenReturn(Optional.of(new Account(1,"x@a.test","hash","USUARIO","ACTIVA",0)));
        var e=assertThrows(ApiException.class,()->service.register(registro("x@a.test","30123456","1990-01-01")));
        assertEquals("EMAIL_ALREADY_EXISTS",e.codigo());
        verify(accounts,never()).newUser(anyLong(),anyString(),anyString(),any());
    }
    @Test void duplicateDniRejectsCreation() {
        when(accounts.byEmail("x@a.test")).thenReturn(Optional.empty());
        when(accounts.existsUserWithDni("30123456")).thenReturn(true);
        var e=assertThrows(ApiException.class,()->service.register(registro("x@a.test","30123456","1990-01-01")));
        assertEquals("DNI_ALREADY_EXISTS",e.codigo());
        verify(accounts,never()).newAccount(any(),any(),any(),any());
    }
    @Test void rejectsMinorsAndInvalidPersonalData() {
        LocalDate hoy=LocalDate.of(2026,11,22);
        // Cumple 18 exactamente hoy: se acepta. Un día menos: se rechaza.
        assertEquals(LocalDate.of(2008,11,22),
            AuthService.datosPersonales(registro("a@a.test","1234567","2008-11-22"),hoy).fechaNacimiento());
        for (String[] caso : new String[][]{
                {"1234567","2008-11-23"}, {"1234567","2030-01-01"}, {"1234567","22/11/1990"}, {"1234567",null},
                {"123456","1990-01-01"}, {"123456789","1990-01-01"}, {"12a4567","1990-01-01"}, {null,"1990-01-01"}})
            assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
                ()->AuthService.datosPersonales(registro("a@a.test",caso[0],caso[1]),hoy)).codigo(),
                String.join(",",java.util.Arrays.asList(caso)));
    }
    @Test void numeroAfiliadoIsDiscardedWithoutObraSocial() {
        var datos=AuthService.datosPersonales(new RegisterRequest("A","B","a@a.test","Password123!",
            "1234567","1990-01-01","",null,"999"),LocalDate.of(2026,1,1));
        assertNull(datos.telefono());
        assertNull(datos.obraSocial());
        assertNull(datos.numeroAfiliado());
    }
    private static RegisterRequest registro(String correo, String dni, String nacimiento) {
        return new RegisterRequest("A","B",correo,"Password123!",dni,nacimiento,null,null,null);
    }
    @Test void rejectsWrongCredentialsAndDisabledAccounts() {
        when(accounts.byEmail("x@a.test")).thenReturn(Optional.of(new Account(1,"x@a.test","hash","USUARIO","DESHABILITADA",0)));
        when(encoder.matches("correct-pass","hash")).thenReturn(true);
        assertEquals("INVALID_CREDENTIALS",assertThrows(ApiException.class,
            ()->service.login(new LoginRequest("x@a.test","incorrect"))).codigo());
        assertEquals("ACCOUNT_DISABLED",assertThrows(ApiException.class,
            ()->service.login(new LoginRequest("x@a.test","correct-pass"))).codigo());
        verify(tokens,never()).create(any());
    }
    @Test void rejectsSamePasswordOrIncorrectCurrentPassword() {
        when(accounts.byIdLocked(1)).thenReturn(new Account(1,"x@a.test","hash","USUARIO","ACTIVA",0));
        when(encoder.matches("current","hash")).thenReturn(true);
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->service.changePassword(new CurrentAccount(1,"USUARIO","jti",0),
                new ChangePasswordRequest("current","short"),mock(HttpServletRequest.class))).codigo());
        assertEquals("CURRENT_PASSWORD_INVALID",assertThrows(ApiException.class,
            ()->service.changePassword(new CurrentAccount(1,"USUARIO","jti",0),
                new ChangePasswordRequest("wrong","NewPassword123!"),mock(HttpServletRequest.class))).codigo());
        when(encoder.matches("NewPassword123!","hash")).thenReturn(true);
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->service.changePassword(new CurrentAccount(1,"USUARIO","jti",0),
                new ChangePasswordRequest("current","NewPassword123!"),mock(HttpServletRequest.class))).codigo());
    }
    @Test void loginAndPasswordChangeRejectMoreThan72Utf8Bytes() {
        // 40 chars but 80 bytes: should be invalid even though character count is < 72.
        String tooManyBytes = "á".repeat(40);
        assertEquals("VALIDATION_ERROR", assertThrows(ApiException.class,
            () -> service.login(new LoginRequest("x@a.test", tooManyBytes))).codigo());
        assertEquals("VALIDATION_ERROR", assertThrows(ApiException.class,
            () -> service.changePassword(new CurrentAccount(1,"USUARIO","jti",0),
                new ChangePasswordRequest(tooManyBytes, "Password123!"),
                mock(HttpServletRequest.class))).codigo());
        verifyNoInteractions(tokens);
    }
    @Test void bcryptCharacterAndByteBoundariesAreCheckedBeforeHashing() {
        assertThrows(ApiException.class,()->AuthService.initialPassword("1234567"));
        assertThrows(ApiException.class,()->AuthService.initialPassword("x".repeat(73)));
        assertThrows(ApiException.class,()->AuthService.initialPassword("á".repeat(40)));
        assertDoesNotThrow(()->AuthService.initialPassword("abcDEF12"));
    }
}
