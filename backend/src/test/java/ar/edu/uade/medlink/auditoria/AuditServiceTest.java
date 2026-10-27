package ar.edu.uade.medlink.auditoria;

import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditServiceTest {
    JdbcTemplate db=mock(JdbcTemplate.class);
    AuditService audit=new AuditService(db,Clock.fixed(Instant.parse("2026-10-20T18:30:00Z"),ZoneOffset.UTC));
    CurrentAccount admin=new CurrentAccount(1,"ADMINISTRADOR","test",0);
    AuditoriaFiltro empty() { return new AuditoriaFiltro(null,null,null,null,null,null,null,null); }

    @Test void invalidDomainsAndIdentifiersAreRejected() {
        assertThrows(ApiException.class,()->new AuditoriaFiltro(-1L,null,null,null,null,null,null,null));
        assertThrows(ApiException.class,()->new AuditoriaFiltro(null,0L,null,null,null,null,null,null));
        assertThrows(ApiException.class,()->new AuditoriaFiltro(null,null,"ADMIN",null,null,null,null,null));
        assertThrows(ApiException.class,()->new AuditoriaFiltro(null,null,null,"DROP TABLE",null,null,null,null));
        assertThrows(ApiException.class,()->new AuditoriaFiltro(null,null,null,null,"INVALIDA",null,null,null));
    }
    @Test void rejectsEmptyReversedAndSubMillisecondRanges() {
        var t=OffsetDateTime.parse("2026-10-20T15:30:00.000-03:00");
        for (var end:List.of(t,t.minusSeconds(1)))
            assertThrows(ApiException.class,()->new AuditoriaFiltro(null,null,null,null,null,null,t,end));
        assertThrows(ApiException.class,()->new AuditoriaFiltro(null,null,null,null,null,null,t.plusNanos(1),null));
        assertDoesNotThrow(()->new AuditoriaFiltro(null,null," SISTEMA ",null,null,null,t,t.plusSeconds(1)));
    }
    @Test void invalidPagingAndSortingFailBeforeAnySql() {
        for (int[] p:List.of(new int[]{-1,20},new int[]{0,0},new int[]{0,101},new int[]{Integer.MAX_VALUE,100}))
            assertThrows(ApiException.class,()->audit.listar(empty(),p[0],p[1],null,admin));
        for (String sort:List.of("accion,asc","fechaHora,asc;DROP TABLE auditoria","id","id,sideways"))
            assertThrows(ApiException.class,()->audit.listar(empty(),0,20,List.of(sort),admin));
        verifyNoInteractions(db);
    }
    @Test void serviceAlsoEnforcesAdministrativeScope() {
        for (String role:List.of("USUARIO","OPERADOR","SISTEMA"))
            assertThrows(ApiException.class,()->audit.listar(empty(),0,20,null,new CurrentAccount(2,role,"t",0)));
        assertThrows(ApiException.class,()->audit.listar(empty(),0,20,null,null));
        verifyNoInteractions(db);
    }
    @Test void prescriptionSummariesRetainSafeOperationalFields() {
        Map<String,Object> values=new LinkedHashMap<>();
        values.put("codigoExterno","RX-2026-TEST");
        values.put("usuarioId",12L);
        values.put("fechaEmision","2026-10-01");
        values.put("fechaVencimiento","2026-11-01");
        values.put("cantidadItems",2);
        values.put("estado","ACTIVA");
        values.put("passwordHash","NEVER_STORE_ME");
        audit.record(1L,"ADMINISTRADOR","CREAR_PRESCRIPCION","PRESCRIPCION",7,12L,
            null,null,values,null);
        Object[] args=mockingDetails(db).getInvocations().iterator().next().getArguments();
        String json=(String)args[args.length-1];
        for (String expected : List.of("codigoExterno","usuarioId","fechaEmision", "fechaVencimiento","cantidadItems"))
            assertTrue(json.contains(expected),expected);
        assertFalse(json.contains("NEVER_STORE_ME"));
    }

    @Test void summariesWhitelistScalarsAndNeverSerializeCredentialsOrNestedObjects() {
        Map<String,Object> values=new LinkedHashMap<>();
        values.put("nombre","Texto\"\n\u0001"); values.put("estado",Map.of("jwt","secreto"));
        for (String key:List.of("password","passwordHash","contrasena","jwt","jti","privateKey","token"))
            values.put(key,"secreto");
        var request=new MockHttpServletRequest();
        request.setAttribute("correlationId","bad");
        audit.record(1L,"ADMINISTRADOR","CREAR_MEDICAMENTO","MEDICAMENTO",2,null,null,null,values,request);
        var invocation=mockingDetails(db).getInvocations().iterator().next();
        Object[] args=invocation.getArguments();
        String json=(String)args[args.length-1];
        assertTrue(json.contains("nombre")); assertTrue(json.contains("\\u0001"));
        assertFalse(json.contains("secreto")); assertFalse(json.contains("estado"));
        assertThrows(IllegalArgumentException.class,()->audit.record(null,"SISTEMA","CREAR_SUSPENSION",
            "SUSPENSION",1,1L,null,null,null,null));
    }
}
