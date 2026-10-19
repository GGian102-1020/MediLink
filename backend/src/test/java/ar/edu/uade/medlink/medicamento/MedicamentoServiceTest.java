package ar.edu.uade.medlink.medicamento;

import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.medicamento.dto.*;
import ar.edu.uade.medlink.medicamento.entity.*;
import ar.edu.uade.medlink.medicamento.repository.MedicamentoRepository;
import ar.edu.uade.medlink.medicamento.service.MedicamentoService;
import ar.edu.uade.medlink.security.CurrentAccount;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicamentoServiceTest {
    @Mock MedicamentoRepository repository;
    @Mock AuditService audit;
    MedicamentoService service;
    CurrentAccount admin=new CurrentAccount(4,"ADMINISTRADOR","t",0);
    @BeforeEach void setup() { service=new MedicamentoService(repository,audit); }
    Medicamento medicine() {
        var m=new Medicamento("Amoxicilina");
        ReflectionTestUtils.setField(m,"id",2L);
        return m;
    }
    @Test void createsAnActiveMedicineAuditingNormalizedName() {
        when(repository.saveAndFlush(any())).thenAnswer(c->{
            Medicamento m=c.getArgument(0); ReflectionTestUtils.setField(m,"id",2L); return m;
        });
        var dto=service.crear(new MedicamentoRequest(" Amoxicilina "),admin,null);
        assertEquals("Amoxicilina",dto.nombre()); assertEquals(2L,dto.id());
        assertEquals(EstadoMedicamento.ACTIVO,dto.estado());
        verify(audit).record(eq(4L),eq("ADMINISTRADOR"),eq("CREAR_MEDICAMENTO"),eq("MEDICAMENTO"),
            eq(2L),isNull(),isNull(),isNull(),argThat(m->m.get("estado").equals("ACTIVO")),isNull());
    }
    @Test void onlyAdminsMutateMedicines() {
        var user=new CurrentAccount(8,"USUARIO","t",0);
        assertEquals("FORBIDDEN",assertThrows(ApiException.class,
            ()->service.crear(new MedicamentoRequest("Prueba"),user,null)).codigo());
        assertEquals("FORBIDDEN",assertThrows(ApiException.class,
            ()->service.cambiarEstado(2,false,"Motivo",user,null)).codigo());
        verifyNoInteractions(repository,audit);
    }
    @Test void invalidNamesAreRejectedBeforeSaving() {
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->service.crear(new MedicamentoRequest("  "),admin,null)).codigo());
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,
            ()->service.crear(new MedicamentoRequest("a".repeat(161)),admin,null)).codigo());
        verifyNoInteractions(repository,audit);
    }
    @Test void validatesNameLengthAfterTrimmingRequest() {
        var padded=new MedicamentoRequest(" "+"A".repeat(160)+" ");
        assertEquals(160,padded.nombre().length());
    }
    @Test void stateChangePreservesRecordAndAuditsPreviousState() {
        var m=medicine(); when(repository.findLocked(2)).thenReturn(Optional.of(m));
        var dto=service.cambiarEstado(2,false," Sin venta ",admin,null);
        assertEquals(EstadoMedicamento.INACTIVO,dto.estado());
        verify(audit).record(eq(4L),eq("ADMINISTRADOR"),eq("DESACTIVAR_MEDICAMENTO"),eq("MEDICAMENTO"),
            eq(2L),isNull(),eq("Sin venta"),argThat(x->x.get("estado").equals("ACTIVO")),
            argThat(x->x.get("estado").equals("INACTIVO")),isNull());
        assertEquals("INVALID_STATE_TRANSITION",assertThrows(ApiException.class,
            ()->service.cambiarEstado(2,false,"Motivo",admin,null)).codigo());
    }
    @Test void unknownMedicineReturnsNormativeNotFound() {
        when(repository.findLocked(42)).thenReturn(Optional.empty());
        assertEquals("RESOURCE_NOT_FOUND",assertThrows(ApiException.class,
            ()->service.editar(42,new MedicamentoRequest("Prueba"),admin,null)).codigo());
    }
    @Test void escapeSearchWildcardsToAvoidTreatingThemAsPatterns() {
        assertEquals("%a!%b!_c!!%",MedicamentoService.pattern(" A%B_C! "));
    }
}
