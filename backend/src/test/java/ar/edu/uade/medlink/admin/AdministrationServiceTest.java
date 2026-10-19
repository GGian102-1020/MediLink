package ar.edu.uade.medlink.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.auth.AccountRepository.Account;
import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdministrationServiceTest {
    JdbcTemplate db=mock(JdbcTemplate.class);
    AccountRepository accounts=mock(AccountRepository.class);
    AdministrationService service=new AdministrationService(db,accounts,mock(PasswordEncoder.class),
        Clock.systemUTC(),mock(AuditService.class),mock(ReservationClosureService.class));
    CurrentAccount admin=new CurrentAccount(1,"ADMINISTRADOR","j",0);

    @Test void cannotDisableAdministratorThroughApi() {
        when(accounts.byIdLocked(1)).thenReturn(new Account(1,"admin@medlink.test","hash","ADMINISTRADOR","ACTIVA",0));
        assertEquals("FORBIDDEN",assertThrows(ApiException.class,
            ()->service.changeState(1,false,"Razón",admin,mock(HttpServletRequest.class))).codigo());
    }
    @Test void cannotDisableLastOperationalPharmacyOperator() {
        when(accounts.byIdLocked(2)).thenReturn(new Account(2,"op@test","hash","OPERADOR","ACTIVA",0));
        when(db.queryForObject(startsWith("SELECT id_farmacia"),eq(Long.class),eq(2L))).thenReturn(6L);
        when(db.query(startsWith("SELECT estado::text"),
            org.mockito.ArgumentMatchers.<RowMapper<String>>any(),eq(6L))).thenReturn(List.of("ACTIVA"));
        when(db.queryForObject(startsWith("SELECT COUNT(*)"),eq(Integer.class),eq(6L))).thenReturn(1);
        assertEquals("LAST_OPERATIONAL_OPERATOR",assertThrows(ApiException.class,
            ()->service.changeState(2,false,"Razón",admin,mock(HttpServletRequest.class))).codigo());
        verify(accounts,never()).changeState(anyLong(),anyString(),anyBoolean());
    }
    @Test void sortingWhitelistRejectsSqlInjection() {
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->AdministrationService.sortClause(List.of("correo,asc;DROP TABLE cuenta"),
                java.util.Map.of("correo","c.correo","id","c.id"),"c.id ASC")).codigo());
        assertEquals("c.correo DESC,c.id ASC",AdministrationService.sortClause(List.of("correo,desc"),
            java.util.Map.of("correo","c.correo","id","c.id"),"c.id ASC"));
        assertEquals("c.id DESC",AdministrationService.sortClause(List.of("id,desc"),
            java.util.Map.of("correo","c.correo","id","c.id"),"c.id ASC"));
    }
}
