package ar.edu.uade.medlink.suspension;

import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import ar.edu.uade.medlink.suspension.entity.MotivoSuspension;
import ar.edu.uade.medlink.suspension.entity.Suspension;
import ar.edu.uade.medlink.suspension.repository.SuspensionRepository;
import ar.edu.uade.medlink.suspension.service.SuspensionService;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SuspensionServiceTest {
    final SuspensionRepository repository=mock(SuspensionRepository.class);
    final Clock clock=Clock.fixed(Instant.parse("2026-10-27T18:00:00Z"),ZoneOffset.UTC);
    final SuspensionService service=new SuspensionService(repository,clock);
    final CurrentAccount admin=new CurrentAccount(1L,"ADMINISTRADOR","jti",0);

    @Test void rejectsWrongRoleBeforeTouchingPersistence() {
        for (String role:List.of("USUARIO","OPERADOR")) {
            var ex=assertThrows(ApiException.class,()->service.listar(5L,null,null,null,0,20,null,
                new CurrentAccount(8,role,"test",0)));
            assertEquals("FORBIDDEN",ex.codigo());
        }
        assertEquals("FORBIDDEN",assertThrows(ApiException.class,
            ()->service.listar(5L,null,null,null,0,20,null,null)).codigo());
        verifyNoInteractions(repository);
    }

    @Test void validatesUserDatesPaginationAndSortBeforeAccess() {
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->service.listar(-1L,null,null,null,0,20,null,admin)).codigo());
        var at=OffsetDateTime.parse("2026-10-27T15:00:00.123-03:00");
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->service.listar(1L,null,at,at,0,20,null,admin)).codigo());
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->service.listar(1L,null,at.plusNanos(1),null,0,20,null,admin)).codigo());
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->service.listar(1L,null,null,null,-1,20,null,admin)).codigo());
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->service.listar(1L,null,null,null,0,101,null,admin)).codigo());
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->service.listar(1L,null,null,null,0,20,List.of("motivo,asc"),admin)).codigo());
        verifyNoInteractions(repository);
    }

    @Test void missingUserReturns404AndExistingHistoryIsEffectiveAtOneServerInstant() {
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(ApiException.class,
            ()->service.listar(8L,null,null,null,0,20,null,admin)).codigo());
        when(repository.existeUsuario(8L)).thenReturn(true);
        var active=row(20L,Instant.parse("2026-10-27T17:00:00Z"),Instant.parse("2026-11-03T17:00:00Z"));
        var expired=row(21L,Instant.parse("2026-10-19T17:00:00Z"),Instant.parse("2026-10-26T17:00:00Z"));
        when(repository.findAll(any(Specification.class),any(Pageable.class)))
            .thenAnswer(inv -> new PageImpl<>(List.of(active,expired),inv.getArgument(1),2));
        var result=service.listar(8L,null,null,null,0,20,null,admin);
        assertEquals(2,result.totalElements());
        assertTrue(result.content().get(0).vigente());
        assertFalse(result.content().get(1).vigente());
        assertEquals("2026-10-27T14:00:00.000-03:00",result.content().get(0).fechaHoraInicio());
        assertEquals("INCUMPLIMIENTOS_REITERADOS",result.content().get(0).motivo());
        assertEquals(20L,result.content().get(0).id());
        verify(repository).findAll(any(Specification.class),argThat((Pageable pg) ->
            pg.getPageNumber()==0 && pg.getPageSize()==20 &&
            pg.getSort().getOrderFor("fechaHoraInicio").isDescending()));
    }

    private static Suspension row(long id, Instant from, Instant to) {
        var s=instantiate();
        ReflectionTestUtils.setField(s,"id",id);
        ReflectionTestUtils.setField(s,"usuarioId",8L);
        ReflectionTestUtils.setField(s,"fechaHoraInicio",from);
        ReflectionTestUtils.setField(s,"fechaHoraFin",to);
        ReflectionTestUtils.setField(s,"motivo",MotivoSuspension.INCUMPLIMIENTOS_REITERADOS);
        return s;
    }
    private static Suspension instantiate() {
        try {
            var ctor=Suspension.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
}
