package ar.edu.uade.medlink.admin;

import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import ar.edu.uade.medlink.suspension.SuspensionConsultaService;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SuspensionConsultaServiceTest {
    private static final CurrentAccount ADMIN = new CurrentAccount(1,"ADMINISTRADOR","jti",0);
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final SuspensionConsultaService service = new SuspensionConsultaService(db,
        Clock.fixed(Instant.parse("2026-10-27T17:00:00Z"), ZoneOffset.UTC));

    @Test void scopeIsEnforcedBeforeQueries() {
        for (String role : List.of("USUARIO", "OPERADOR")) {
            var caller = new CurrentAccount(2, role, "jti", 0);
            assertEquals("FORBIDDEN", assertThrows(ApiException.class,
                () -> service.listar(7,null,null,null,0,20,null,caller)).codigo());
        }
        assertEquals("FORBIDDEN", assertThrows(ApiException.class,
            () -> service.listar(7,null,null,null,0,20,null,null)).codigo());
        verifyNoInteractions(db);
    }

    @Test void validatesIdentifiersRangesPagingAndSortingBeforeSql() {
        for (long id : List.of(0L,-1L))
            assertEquals("VALIDATION_ERROR", assertThrows(ApiException.class,
                () -> service.listar(id,null,null,null,0,20,null,ADMIN)).codigo());
        var t = OffsetDateTime.parse("2026-10-27T14:00:00.000-03:00");
        for (var end : List.of(t,t.minusSeconds(1)))
            assertEquals("VALIDATION_ERROR", assertThrows(ApiException.class,
                () -> service.listar(7,null,t,end,0,20,null,ADMIN)).codigo());
        assertThrows(ApiException.class, () -> service.listar(7,null,t.plusNanos(1),null,0,20,null,ADMIN));
        for (String invalid : List.of("fechaHoraFin,asc", "id", "id,asc;DROP TABLE suspension"))
            assertThrows(ApiException.class, () -> service.listar(7,null,null,null,0,20,List.of(invalid),ADMIN));
        for (int[] values : List.of(new int[]{-1,20},new int[]{0,0},new int[]{0,101}))
            assertThrows(ApiException.class, () -> service.listar(7,null,null,null,values[0],values[1],null,ADMIN));
        verifyNoInteractions(db);
    }

    @Test void unknownUserIs404RatherThanEmptyHistory() {
        when(db.queryForObject(contains("SELECT EXISTS"),eq(Boolean.class),eq(77L))).thenReturn(false);
        assertEquals("RESOURCE_NOT_FOUND", assertThrows(ApiException.class,
            () -> service.listar(77,null,null,null,0,20,null,ADMIN)).codigo());
    }
    @Test void historyQueryUsesCorrectScopeTimeWindowAndStableOrder() {
        class Stub extends JdbcTemplate {
            String sql;
            Object[] args;
            @Override public <T> T queryForObject(String query, Class<T> type, Object... args) {
                if (query.startsWith("SELECT EXISTS")) return type.cast(Boolean.TRUE);
                if (query.startsWith("SELECT COUNT")) return type.cast(0L);
                throw new AssertionError(query);
            }
            @Override public <T> List<T> query(String query,
                org.springframework.jdbc.core.RowMapper<T> mapper, Object... parameters) {
                sql=query;
                args=parameters;
                return List.of();
            }
        }
        var stub=new Stub();
        var history=new SuspensionConsultaService(stub,
            Clock.fixed(Instant.parse("2026-10-27T17:00:00Z"), ZoneOffset.UTC));
        var from=OffsetDateTime.parse("2026-10-01T10:00:00.000-03:00");
        var until=OffsetDateTime.parse("2026-11-01T10:00:00.000-03:00");
        var result=history.listar(7,true,from,until,2,15,List.of("fechaHoraInicio,asc"),ADMIN);
        assertEquals(0,result.totalElements());
        assertEquals(2,result.page());
        assertTrue(stub.sql.contains("s.id_usuario=?"));
        assertTrue(stub.sql.contains("s.fecha_hora_inicio<=? AND s.fecha_hora_fin>?"));
        assertTrue(stub.sql.contains("s.fecha_hora_inicio>=? AND s.fecha_hora_inicio<?"));
        assertTrue(stub.sql.contains("s.fecha_hora_inicio ASC,s.id_suspension ASC"));
        assertEquals(30L,stub.args[stub.args.length-1]);
        history.listar(7,false,null,null,0,20,null,ADMIN);
        assertTrue(stub.sql.contains("s.fecha_hora_inicio>? OR s.fecha_hora_fin<=?"));
        assertTrue(stub.sql.contains("s.fecha_hora_inicio DESC,s.id_suspension DESC"));
    }

}
