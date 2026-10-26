package ar.edu.uade.medlink.prescripcion;

import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.prescripcion.entity.*;
import ar.edu.uade.medlink.prescripcion.repository.*;
import ar.edu.uade.medlink.prescripcion.service.MockPrescriptionValidationService;
import ar.edu.uade.medlink.presentacion.entity.CondicionDispensa;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockPrescriptionValidationServiceTest {
    // 02:30 UTC is still 24/10 in Argentina: validity must use the Argentine calendar date.
    static final Instant NOW=Instant.parse("2026-10-25T02:30:00Z");
    static final LocalDate HOY_AR=LocalDate.of(2026,10,24);
    @Mock PrescripcionRepository prescriptions;
    @Mock ItemPrescripcionRepository items;
    MockPrescriptionValidationService service;
    @BeforeEach void setup() {
        service=new MockPrescriptionValidationService(prescriptions,items,Clock.fixed(NOW,ApiClock.ZONE));
    }
    Prescripcion rx(long user, LocalDate desde, LocalDate hasta) {
        var p=new Prescripcion("RX-1",user,desde,hasta,NOW);
        ReflectionTestUtils.setField(p,"id",5L);
        return p;
    }
    ItemPrescripcion item(Prescripcion p, int autorizada, int comprometida, int utilizada) {
        ReflectionTestUtils.setField(p,"id",null);
        var i=p.agregarItem(PrescripcionServiceTest.presentation(6,CondicionDispensa.BAJO_RECETA),autorizada);
        ReflectionTestUtils.setField(p,"id",5L);
        ReflectionTestUtils.setField(i,"cantidadComprometida",comprometida);
        ReflectionTestUtils.setField(i,"cantidadUtilizada",utilizada);
        return i;
    }
    String code(Runnable call) { return assertThrows(ApiException.class,call::run).codigo(); }

    @Test void requiresCodeAndPositiveQuantity() {
        assertEquals("VALIDATION_ERROR",code(()->service.validar(1,"  ",6,1)));
        assertEquals("VALIDATION_ERROR",code(()->service.validar(1,"RX-1",6,0)));
        verifyNoInteractions(prescriptions,items);
    }
    @Test void missingOrForeignPrescriptionLooksTheSame() {
        when(prescriptions.findLockedByCodigo("RX-9")).thenReturn(Optional.empty());
        assertEquals("PRESCRIPTION_NOT_FOUND",code(()->service.validar(1,"RX-9",6,1)));
        when(prescriptions.findLockedByCodigo("RX-1")).thenReturn(Optional.of(rx(2,HOY_AR,HOY_AR)));
        assertEquals("PRESCRIPTION_NOT_FOUND",code(()->service.validar(1," RX-1 ",6,1)));
        verifyNoInteractions(items);
    }
    @Test void annulledPrescriptionIsRejected() {
        var p=rx(1,HOY_AR,HOY_AR); p.anular();
        when(prescriptions.findLockedByCodigo("RX-1")).thenReturn(Optional.of(p));
        assertEquals("PRESCRIPTION_ANNULLED",code(()->service.validar(1,"RX-1",6,1)));
    }
    @Test void validityBoundsAreInclusiveInArgentina() {
        when(prescriptions.findLockedByCodigo("RX-1")).thenReturn(Optional.of(rx(1,HOY_AR.minusDays(5),HOY_AR.minusDays(1))));
        assertEquals("PRESCRIPTION_EXPIRED",code(()->service.validar(1,"RX-1",6,1)));
        when(prescriptions.findLockedByCodigo("RX-1")).thenReturn(Optional.of(rx(1,HOY_AR.plusDays(1),HOY_AR.plusDays(5))));
        assertEquals("PRESCRIPTION_EXPIRED",code(()->service.validar(1,"RX-1",6,1)));
        var lastDay=rx(1,HOY_AR.minusDays(5),HOY_AR);
        var i=item(lastDay,1,0,0);
        when(prescriptions.findLockedByCodigo("RX-1")).thenReturn(Optional.of(lastDay));
        when(items.findLocked(5L,6L)).thenReturn(Optional.of(i));
        assertSame(i,service.validar(1,"RX-1",6,1));
    }
    @Test void presentationMustBeAuthorized() {
        when(prescriptions.findLockedByCodigo("RX-1")).thenReturn(Optional.of(rx(1,HOY_AR,HOY_AR)));
        when(items.findLocked(5L,7L)).thenReturn(Optional.empty());
        assertEquals("PRESCRIPTION_NOT_AUTHORIZED",code(()->service.validar(1,"RX-1",7,1)));
    }
    @Test void committedAndUsedUnitsReduceAvailability() {
        var p=rx(1,HOY_AR,HOY_AR);
        var i=item(p,3,1,1);
        when(prescriptions.findLockedByCodigo("RX-1")).thenReturn(Optional.of(p));
        when(items.findLocked(5L,6L)).thenReturn(Optional.of(i));
        assertEquals("PRESCRIPTION_QUANTITY_EXCEEDED",code(()->service.validar(1,"RX-1",6,2)));
        assertSame(i,service.validar(1,"RX-1",6,1));
    }
}
