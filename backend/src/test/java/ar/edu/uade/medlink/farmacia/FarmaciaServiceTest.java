package ar.edu.uade.medlink.farmacia;

import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.farmacia.dto.*;
import ar.edu.uade.medlink.farmacia.entity.*;
import ar.edu.uade.medlink.farmacia.repository.*;
import ar.edu.uade.medlink.farmacia.service.FarmaciaService;
import ar.edu.uade.medlink.security.CurrentAccount;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FarmaciaServiceTest {
    @Mock FarmaciaRepository repository;
    @Mock FarmaciaIntegration integration;
    @Mock AuditService audit;
    FarmaciaService service;
    final Instant t=Instant.parse("2026-10-16T14:00:00Z");
    final CurrentAccount admin=new CurrentAccount(1,"ADMINISTRADOR","test",0);
    @BeforeEach void setUp() { service=new FarmaciaService(repository,integration,audit,Clock.fixed(t,ZoneOffset.UTC)); }
    Farmacia farmacia() {
        var f=new Farmacia("Central","Calle 1",null,null);
        ReflectionTestUtils.setField(f,"id",4L); return f;
    }
    @Test void listingLoadsOperationalStateInOneBatch() {
        var active=farmacia();
        var inactive=new Farmacia("Sur", "Calle 2", null, null);
        ReflectionTestUtils.setField(inactive,"id",5L);
        inactive.cambiarEstado(EstadoFarmacia.INACTIVA);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(active,inactive),PageRequest.of(0,20),2));
        when(integration.farmaciasConOperadorActivo(Set.of(4L,5L))).thenReturn(Set.of(4L,5L));
        var response=service.listar(null,null,0,20,null,admin);
        assertEquals(2,response.content().size());
        assertTrue(response.content().get(0).operativa());
        assertFalse(response.content().get(1).operativa());
        verify(integration,never()).tieneOperadorActivo(anyLong());
        verify(integration).farmaciasConOperadorActivo(Set.of(4L,5L));
    }
    @Test void createsActiveButNonOperationalWithoutOperatorsAndAudits() {
        when(repository.saveAndFlush(any())).thenAnswer(call->{
            Farmacia f=call.getArgument(0); ReflectionTestUtils.setField(f,"id",4L);return f;
        });
        var dto=service.crear(new FarmaciaRequest(" Nueva "," Calle 3 "," ",null),admin,null);
        assertEquals(EstadoFarmacia.ACTIVA,dto.estado()); assertFalse(dto.operativa());
        assertEquals("Nueva",dto.nombre()); assertNull(dto.telefono());
        verify(integration, never()).tieneOperadorActivo(anyLong());
        verify(audit).record(eq(1L),eq("ADMINISTRADOR"),eq("CREAR_FARMACIA"),eq("FARMACIA"),eq(4L),
            isNull(),isNull(),isNull(),argThat(m->m.get("nombre").equals("Nueva")),isNull());
    }
    @Test void editsContactAndPreservesState() {
        var f=farmacia(); f.cambiarEstado(EstadoFarmacia.INACTIVA);
        when(repository.findLocked(4)).thenReturn(Optional.of(f));
        var dto=service.editar(4,new FarmaciaRequest("Editada","Otra", "123","a@b.test"),admin,null);
        assertEquals("Editada",dto.nombre()); assertEquals(EstadoFarmacia.INACTIVA,dto.estado());
        verify(audit).record(eq(1L),anyString(),eq("EDITAR_FARMACIA"),eq("FARMACIA"),eq(4L),isNull(),isNull(),
            argThat(m->m.get("nombre").equals("Central")),argThat(m->m.get("nombre").equals("Editada")),isNull());
    }
    @Test void cannotDeactivateWithFunctionallyActiveReservations() {
        var f=farmacia();when(repository.findLocked(4)).thenReturn(Optional.of(f));
        when(integration.tieneReservasActivas(4,t)).thenReturn(true);
        assertEquals("PHARMACY_HAS_ACTIVE_RESERVATIONS",assertThrows(ApiException.class,
            ()->service.cambiarEstado(4,false,"Cierre",admin,null)).codigo());
        assertEquals(EstadoFarmacia.ACTIVA,f.getEstado()); verifyNoInteractions(audit);
    }
    @Test void canDeactivateWhenOnlyExpiredReservationsRemain() {
        when(repository.findLocked(4)).thenReturn(Optional.of(farmacia()));
        var dto=service.cambiarEstado(4,false," Cierre ",admin,null);
        assertEquals(EstadoFarmacia.INACTIVA,dto.estado()); assertFalse(dto.operativa());
        verify(integration).tieneReservasActivas(4,t);
    }
    @Test void reactivationDoesNotRequireAnOperatorButOperationalFlagDoes() {
        var f=farmacia();f.cambiarEstado(EstadoFarmacia.INACTIVA);
        when(repository.findLocked(4)).thenReturn(Optional.of(f));
        assertFalse(service.cambiarEstado(4,true,"Apertura",admin,null).operativa());
    }
    @Test void reactivationWithActiveOperatorIsOperational() {
        var f=farmacia();f.cambiarEstado(EstadoFarmacia.INACTIVA);
        when(repository.findLocked(4)).thenReturn(Optional.of(f)); when(integration.tieneOperadorActivo(4)).thenReturn(true);
        assertTrue(service.cambiarEstado(4,true,"Apertura",admin,null).operativa());
    }
    @Test void sameStateAndBlankReasonAreRejected() {
        when(repository.findLocked(4)).thenReturn(Optional.of(farmacia()));
        assertEquals("INVALID_STATE_TRANSITION",assertThrows(ApiException.class,
            ()->service.cambiarEstado(4,true,"Razón",admin,null)).codigo());
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->service.cambiarEstado(4,false," ",admin,null)).codigo());
    }
    @Test void operatorsAndUsersCannotAdministerPharmacies() {
        for (String role:List.of("OPERADOR","USUARIO")) {
            var actor=new CurrentAccount(2,role,"test",0);
            assertEquals("FORBIDDEN",assertThrows(ApiException.class,
                ()->service.crear(new FarmaciaRequest("Nueva","Calle",null,null),actor,null)).codigo());
            assertThrows(ApiException.class,()->service.editar(4,new FarmaciaRequest("Nueva","Calle",null,null),actor,null));
            assertThrows(ApiException.class,()->service.cambiarEstado(4,false,"Cierre",actor,null));
            assertThrows(ApiException.class,()->service.listar(null,null,0,20,null,actor));
        }
        verifyNoInteractions(repository,audit);
    }
    @Test void rejectsMissingPharmacyAndInvalidFilters() {
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(ApiException.class,
            ()->service.editar(99,new FarmaciaRequest("Nueva","Calle",null,null),admin,null)).codigo());
        assertThrows(ApiException.class,()->service.listar(null,"INVALIDO",0,20,null,admin));
        assertThrows(ApiException.class,()->service.listar(null,null,0,101,null,admin));
        assertThrows(ApiException.class,()->service.listar(null,null,0,20,List.of("nombre;DROP TABLE farmacia,asc"),admin));
    }
}
