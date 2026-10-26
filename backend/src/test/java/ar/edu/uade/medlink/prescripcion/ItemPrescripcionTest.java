package ar.edu.uade.medlink.prescripcion;

import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.prescripcion.entity.*;
import ar.edu.uade.medlink.presentacion.entity.CondicionDispensa;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemPrescripcionTest {
    ItemPrescripcion item(int autorizada) {
        var p=new Prescripcion("RX-1",1,LocalDate.of(2026,10,1),LocalDate.of(2026,11,1),Instant.EPOCH);
        return p.agregarItem(PrescripcionServiceTest.presentation(6,CondicionDispensa.BAJO_RECETA),autorizada);
    }
    @Test void authorizedQuantityMustBePositive() {
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,()->item(0)).codigo());
    }
    // RN-13: a committed unit cannot back a second simultaneous reservation.
    @Test void commitCannotExceedAuthorization() {
        var i=item(1);
        i.comprometer(1);
        assertEquals(0,i.getCantidadDisponible());
        assertEquals("PRESCRIPTION_QUANTITY_INSUFFICIENT",assertThrows(ApiException.class,()->i.comprometer(1)).codigo());
    }
    // Cancelled/expired reservations free the commitment for reuse.
    @Test void releaseRestoresAvailability() {
        var i=item(2);
        i.comprometer(2); i.liberar(1);
        assertEquals(1,i.getCantidadComprometida()); assertEquals(1,i.getCantidadDisponible());
        assertEquals("INVALID_STATE_TRANSITION",assertThrows(ApiException.class,()->i.liberar(2)).codigo());
    }
    // Completed reservations consume the units definitively.
    @Test void useMovesCommittedUnitsToUsed() {
        var i=item(3);
        i.comprometer(2); i.utilizar(2);
        assertEquals(0,i.getCantidadComprometida()); assertEquals(2,i.getCantidadUtilizada());
        assertEquals(1,i.getCantidadDisponible());
        assertEquals("INVALID_STATE_TRANSITION",assertThrows(ApiException.class,()->i.utilizar(1)).codigo());
        assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,()->i.comprometer(0)).codigo());
    }
}
