package ar.edu.uade.medlink.prescripcion.service;

import ar.edu.uade.medlink.common.error.ApiException;
import java.time.LocalDate;

/** Functional validation contract from Medlink v1.4.3, section 13.1. */
public record PrescriptionValidationResult(
    Status resultado, Long prescriptionId, Long itemPrescripcionId, Long presentacionId,
    Integer cantidadAutorizada, Integer cantidadComprometida, Integer cantidadUtilizada,
    Integer cantidadRestante, LocalDate fechaVencimiento) {

    public enum Status {
        VALID, NOT_FOUND, NOT_OWNER, PRESCRIPTION_ANNULLED, NOT_YET_VALID, EXPIRED,
        MEDICATION_NOT_AUTHORIZED, INSUFFICIENT_QUANTITY
    }

    public static PrescriptionValidationResult rechazo(Status resultado) {
        if (resultado == Status.VALID) throw new IllegalArgumentException("VALID requiere datos del ítem.");
        return new PrescriptionValidationResult(resultado,null,null,null,null,null,null,null,null);
    }

    public void exigirValida() {
        if (resultado == Status.VALID) return;
        if (resultado == Status.INSUFFICIENT_QUANTITY)
            throw ApiException.conflict("PRESCRIPTION_QUANTITY_INSUFFICIENT", "La receta no autoriza la cantidad solicitada.");
        // All other failures intentionally collapse into one public code (11.9/13.1).
        throw ApiException.conflict("PRESCRIPTION_INVALID", "La prescripción no es válida para esta reserva.");
    }
}
