package ar.edu.uade.medlink.presentacion;

import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.medicamento.entity.*;
import ar.edu.uade.medlink.medicamento.repository.MedicamentoRepository;
import ar.edu.uade.medlink.presentacion.dto.*;
import ar.edu.uade.medlink.presentacion.entity.*;
import ar.edu.uade.medlink.presentacion.repository.PresentacionRepository;
import ar.edu.uade.medlink.presentacion.service.PresentacionService;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
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
class PresentacionServiceTest {
    @Mock PresentacionRepository presentations;
    @Mock MedicamentoRepository medicines;
    @Mock EntityManager em;
    @Mock AuditService audit;
    @Mock Query query;
    PresentacionService service;
    CurrentAccount admin=new CurrentAccount(4,"ADMINISTRADOR","t",0);
    @BeforeEach void setup() { service=new PresentacionService(presentations,medicines,em,audit); }
    Medicamento medicine() {
        var m=new Medicamento("Ibuprofeno"); ReflectionTestUtils.setField(m,"id",2L);return m;
    }
    PresentacionCreateRequest request() {
        return new PresentacionCreateRequest(2L," 500  mg "," Comprimidos ",10,"Caja",CondicionDispensa.VENTA_LIBRE);
    }
    @Test void normalizesAndValidatesPresentationText() {
        assertEquals("10 mg",PresentacionService.normalize(" 10    mg "));
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->PresentacionService.normalize(" ")).codigo());
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->service.crear(new PresentacionCreateRequest(2L,"10 mg","capsulas",0,"caja",CondicionDispensa.VENTA_LIBRE),admin,null)).codigo());
        verifyNoInteractions(presentations,medicines,audit);
    }
    @Test void onlyAdminsCanCreatePresentations() {
        var user=new CurrentAccount(9,"OPERADOR","t",0);
        assertEquals("FORBIDDEN",assertThrows(ApiException.class,
            ()->service.crear(request(),user,null)).codigo());
        verifyNoInteractions(presentations,medicines,audit);
    }
    @Test void normalizedRequestValidatesLengthAfterWhitespaceCollapse() {
        var req=new PresentacionCreateRequest(2L," 10   mg "," comprimidos  ",2," caja ",CondicionDispensa.VENTA_LIBRE);
        assertEquals("10 mg",req.concentracionTexto());
        assertEquals("comprimidos",req.formaFarmaceutica());
        assertEquals("caja",req.unidadExpendio());
        var padded=new PresentacionCreateRequest(2L," "+"A".repeat(80)+" ","capsulas",10,"caja",CondicionDispensa.VENTA_LIBRE);
        assertEquals(80,padded.concentracionTexto().length());
    }
    @Test void createsOnlyDeclaredFieldsAndAudits() {
        when(medicines.findLocked(2L)).thenReturn(Optional.of(medicine()));
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(),any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        when(presentations.saveAndFlush(any())).thenAnswer(c->{
            PresentacionMedicamento p=c.getArgument(0); ReflectionTestUtils.setField(p,"id",7L); return p;
        });
        var dto=service.crear(request(),admin,null);
        assertEquals(7L,dto.id()); assertEquals("500 mg",dto.concentracionTexto());
        assertEquals(10,dto.cantidadEnvase()); assertEquals(EstadoPresentacion.ACTIVA,dto.estado());
        verify(audit).record(eq(4L),eq("ADMINISTRADOR"),eq("CREAR_PRESENTACION"),eq("PRESENTACION"),
            eq(7L),isNull(),isNull(),isNull(),anyMap(),isNull());
    }
    @Test void creatingPresentationOnInactiveMedicationDoesNotInventAnExtraRestriction() {
        var med=medicine();med.cambiarEstado(EstadoMedicamento.INACTIVO);
        when(medicines.findLocked(2L)).thenReturn(Optional.of(med));
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(),any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        when(presentations.saveAndFlush(any())).thenAnswer(c->{
            PresentacionMedicamento p=c.getArgument(0);
            ReflectionTestUtils.setField(p,"id",8L);
            return p;
        });
        assertEquals(EstadoPresentacion.ACTIVA,service.crear(request(),admin,null).estado());
    }
    @Test void exactDuplicateReturns409() {
        when(medicines.findLocked(2L)).thenReturn(Optional.of(medicine()));
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(),any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(22L));
        assertEquals("PRESENTATION_DUPLICATE",assertThrows(ApiException.class,
            ()->service.crear(request(),admin,null)).codigo());
        verifyNoInteractions(presentations,audit);
    }
    @Test void structuralConditionIsImmutableAndReceiptCheckIsCorrect() {
        var p=new PresentacionMedicamento(medicine(),"10 mg","comprimidos",10,"caja",CondicionDispensa.BAJO_RECETA);
        assertTrue(p.requiereReceta());
        var free=new PresentacionMedicamento(medicine(),"10 mg","comprimidos",10,"caja",CondicionDispensa.VENTA_LIBRE);
        assertFalse(free.requiereReceta());
    }
}
