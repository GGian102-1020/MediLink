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
        var response=service.register(new RegisterRequest(" Ana ","Paz "," User@Example.test ","Password123!"));
        assertEquals(7,response.usuarioId());
        verify(accounts).newUser(21,"Ana","Paz",DatosPersonales.VACIOS);
    }
    @Test void registrationNormalizesAndStoresPersonalData() {
        when(accounts.byEmail("ana@t.test")).thenReturn(Optional.empty());
        when(accounts.dniExists("30123456")).thenReturn(false);
        when(encoder.encode("Password123!")).thenReturn("$2a$valid");
        when(accounts.newAccount(eq("ana@t.test"),eq("$2a$valid"),eq("USUARIO"),any())).thenReturn(21L);
        var nacimiento = java.time.LocalDate.of(1990, 5, 20);
        service.register(new RegisterRequest("Ana","Paz","ana@t.test","Password123!",
            "30.123.456", nacimiento, "11 4567-8900", " OSDE ", "123/45"));
        verify(accounts).newUser(21,"Ana","Paz",
            new DatosPersonales("30123456", nacimiento, "1145678900", "OSDE", "123/45"));
    }
    @Test void personalDataValidationRejectsInvalidValues() {
        var hoy = java.time.LocalDate.of(2026, 11, 12);
        assertThrows(ApiException.class, () -> AuthService.personalData(
            new RegisterRequest("A","B","a@t.test","x","123",null,null,null,null), hoy));
        assertThrows(ApiException.class, () -> AuthService.personalData(
            new RegisterRequest("A","B","a@t.test","x",null,hoy.plusDays(1),null,null,null), hoy));
        assertThrows(ApiException.class, () -> AuthService.personalData(
            new RegisterRequest("A","B","a@t.test","x",null,null,"abc",null,null), hoy));
        assertThrows(ApiException.class, () -> AuthService.personalData(
            new RegisterRequest("A","B","a@t.test","x",null,null,null,null,"999"), hoy));
        assertEquals(DatosPersonales.VACIOS, AuthService.personalData(
            new RegisterRequest("A","B","a@t.test","x"," ",null,"",null,null), hoy));
    }
    @Test void duplicateDniRejectsCreation() {
        when(accounts.byEmail("n@t.test")).thenReturn(Optional.empty());
        when(accounts.dniExists("30123456")).thenReturn(true);
        var e=assertThrows(ApiException.class,()->service.register(new RegisterRequest("A","B","n@t.test",
            "Password123!","30123456",null,null,null,null)));
        assertEquals("DNI_ALREADY_EXISTS",e.codigo());
        verify(accounts,never()).newAccount(anyString(),anyString(),anyString(),any());
    }
    @Test void duplicateEmailRejectsCreation() {
        when(accounts.byEmail("x@a.test")).thenReturn(Optional.of(new Account(1,"x@a.test","hash","USUARIO","ACTIVA",0)));
        var e=assertThrows(ApiException.class,()->service.register(new RegisterRequest("A","B","x@a.test","Password123!")));
        assertEquals("EMAIL_ALREADY_EXISTS",e.codigo());
        verify(accounts,never()).newUser(anyLong(),anyString(),anyString(),any());
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
