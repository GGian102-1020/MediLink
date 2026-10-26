package ar.edu.uade.medlink.prescripcion;

import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.medicamento.entity.Medicamento;
import ar.edu.uade.medlink.prescripcion.dto.PrescripcionCreateRequest;
import ar.edu.uade.medlink.prescripcion.entity.*;
import ar.edu.uade.medlink.prescripcion.repository.PrescripcionRepository;
import ar.edu.uade.medlink.prescripcion.service.PrescripcionService;
import ar.edu.uade.medlink.presentacion.entity.*;
import ar.edu.uade.medlink.presentacion.repository.PresentacionRepository;
import ar.edu.uade.medlink.security.CurrentAccount;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrescripcionServiceTest {
    static final LocalDate HOY=LocalDate.of(2026,10,25);
    @Mock PrescripcionRepository prescriptions;
    @Mock PresentacionRepository presentations;
    @Mock AuditService audit;
    PrescripcionService service;
    CurrentAccount admin=new CurrentAccount(4,"ADMINISTRADOR","t",0);
    CurrentAccount patient=new CurrentAccount(20,"USUARIO","t",0);
    @BeforeEach void setup() {
        var clock=Clock.fixed(Instant.parse("2026-10-25T15:00:00Z"),ApiClock.ZONE);
        service=new PrescripcionService(prescriptions,presentations,audit,clock);
    }
    static PresentacionMedicamento presentation(long id, CondicionDispensa condicion) {
        var m=new Medicamento("Amoxicilina"); ReflectionTestUtils.setField(m,"id",3L);
        var p=new PresentacionMedicamento(m,"500 mg","Comprimidos",16,"Caja",condicion);
        ReflectionTestUtils.setField(p,"id",id);
        return p;
    }
    static Prescripcion prescription(long id, long user) {
        var p=new Prescripcion("RX-1",user,HOY.minusDays(1),HOY.plusDays(10),Instant.parse("2026-10-24T12:00:00Z"));
        p.agregarItem(presentation(6,CondicionDispensa.BAJO_RECETA),2);
        persisted(p,id);
        return p;
    }
    // Mimics the identifiers PostgreSQL assigns on flush.
    static void persisted(Prescripcion p, long id) {
        ReflectionTestUtils.setField(p,"id",id);
        for (var item:p.getItems()) ReflectionTestUtils.setField(item,"id",id*100+item.getPresentacion().getId());
    }
    PrescripcionCreateRequest request(List<PrescripcionCreateRequest.Item> items) {
        return new PrescripcionCreateRequest("  RX-2026-1 ",1L,HOY,HOY.plusDays(30),items);
    }
    PrescripcionCreateRequest request() {
        return request(List.of(new PrescripcionCreateRequest.Item(6L,2)));
    }

    @Test void onlyAdminsCanCreateOrAnnul() {
        var operator=new CurrentAccount(9,"OPERADOR","t",0);
        assertEquals("FORBIDDEN",assertThrows(ApiException.class,()->service.crear(request(),operator,null)).codigo());
        assertEquals("FORBIDDEN",assertThrows(ApiException.class,()->service.anular(1,"x",patient,null)).codigo());
        verifyNoInteractions(prescriptions,presentations,audit);
    }
    @Test void rejectsIssueDateAfterExpiration() {
        var req=new PrescripcionCreateRequest("RX",1L,HOY,HOY.minusDays(1),List.of(new PrescripcionCreateRequest.Item(6L,1)));
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,()->service.crear(req,admin,null)).codigo());
        verifyNoInteractions(prescriptions,audit);
    }
    @Test void rejectsRepeatedPresentationAndNonPositiveQuantity() {
        var repeated=request(List.of(new PrescripcionCreateRequest.Item(6L,1),new PrescripcionCreateRequest.Item(6L,2)));
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,()->service.crear(repeated,admin,null)).codigo());
        var zero=request(List.of(new PrescripcionCreateRequest.Item(6L,0)));
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,()->service.crear(zero,admin,null)).codigo());
        var empty=request(List.of());
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,()->service.crear(empty,admin,null)).codigo());
        verifyNoInteractions(prescriptions,audit);
    }
    @Test void unknownUserIsNotFound() {
        when(prescriptions.existeUsuario(1L)).thenReturn(false);
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(ApiException.class,()->service.crear(request(),admin,null)).codigo());
        verify(prescriptions,never()).saveAndFlush(any());
    }
    @Test void duplicateExternalCodeReturns409() {
        when(prescriptions.existeUsuario(1L)).thenReturn(true);
        when(prescriptions.existsByCodigoExterno("RX-2026-1")).thenReturn(true);
        assertEquals("PRESCRIPTION_DUPLICATE",assertThrows(ApiException.class,()->service.crear(request(),admin,null)).codigo());
        verify(prescriptions,never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }
    @Test void overTheCounterPresentationCannotBePrescribed() {
        when(prescriptions.existeUsuario(1L)).thenReturn(true);
        when(presentations.findById(6L)).thenReturn(Optional.of(presentation(6,CondicionDispensa.VENTA_LIBRE)));
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,()->service.crear(request(),admin,null)).codigo());
        verify(prescriptions,never()).saveAndFlush(any());
    }
    @Test void createsPrescriptionWithItemsAndAudits() {
        when(prescriptions.existeUsuario(1L)).thenReturn(true);
        when(presentations.findById(6L)).thenReturn(Optional.of(presentation(6,CondicionDispensa.BAJO_RECETA)));
        when(prescriptions.saveAndFlush(any())).thenAnswer(c->{
            Prescripcion p=c.getArgument(0); persisted(p,30L); return p;
        });
        var dto=service.crear(request(),admin,null);
        assertEquals(30L,dto.id()); assertEquals("RX-2026-1",dto.codigoExterno());
        assertEquals(EstadoPrescripcion.ACTIVA,dto.estado()); assertTrue(dto.vigente());
        assertEquals(1,dto.items().size()); assertEquals(2,dto.items().getFirst().cantidadDisponible());
        assertEquals("500 mg - Comprimidos - Caja x 16",dto.items().getFirst().presentacionDescripcion());
        verify(audit).record(eq(4L),eq("ADMINISTRADOR"),eq("CREAR_PRESCRIPCION"),eq("PRESCRIPCION"),
            eq(30L),eq(1L),isNull(),isNull(),anyMap(),isNull());
    }
    @Test void itemsCannotBeAddedAfterRegistration() {
        var p=prescription(5,1);
        assertThrows(IllegalStateException.class,()->p.agregarItem(presentation(7,CondicionDispensa.BAJO_RECETA),1));
    }
    @Test void annulRequiresReasonAndIsIrreversible() {
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,()->service.anular(5,"  ",admin,null)).codigo());
        var p=prescription(5,1);
        when(prescriptions.findLocked(5)).thenReturn(Optional.of(p));
        var dto=service.anular(5," Emitida con error ",admin,null);
        assertEquals(EstadoPrescripcion.ANULADA,dto.estado()); assertFalse(dto.vigente());
        verify(audit).record(eq(4L),eq("ADMINISTRADOR"),eq("ANULAR_PRESCRIPCION"),eq("PRESCRIPCION"),
            eq(5L),eq(1L),eq("Emitida con error"),anyMap(),anyMap(),isNull());
        assertEquals("INVALID_STATE_TRANSITION",assertThrows(ApiException.class,
            ()->service.anular(5,"Otra vez",admin,null)).codigo());
        verifyNoMoreInteractions(audit);
    }
    @Test void patientOnlySeesOwnPrescriptions() {
        when(prescriptions.usuarioDeCuenta(20)).thenReturn(Optional.of(1L));
        when(prescriptions.findById(5L)).thenReturn(Optional.of(prescription(5,1)));
        when(prescriptions.findById(6L)).thenReturn(Optional.of(prescription(6,2)));
        assertEquals(5L,service.detallePropio(5,patient).id());
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(ApiException.class,()->service.detallePropio(6,patient)).codigo());
        var operator=new CurrentAccount(9,"OPERADOR","t",0);
        assertEquals("FORBIDDEN",assertThrows(ApiException.class,()->service.detallePropio(5,operator)).codigo());
    }
    @Test void invalidAdminStateFilterIsRejected() {
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->service.listarAdmin(null,"VENCIDA",null,0,20,null,admin)).codigo());
    }
}
