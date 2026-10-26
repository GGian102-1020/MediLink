package ar.edu.uade.medlink.prescripcion.service;

import java.time.Instant;

/** Replaceable prescription-validation boundary (RN-24/13.1).
 * Calls must occur within the reservation transaction, after canonical locks (10.2).
 * The caller supplies one decision instant T (CV-05) for deterministic validity.
 */
public interface PrescriptionValidationService {
    PrescriptionValidationResult validar(long usuarioId, String codigoPrescripcion, long presentacionId,
                                         int cantidad, Instant instanteDecision);

    /** Apply the validated authorization under the same transaction and canonical locks. */
    void comprometer(long prescriptionId, long itemPrescripcionId, long presentacionId, int cantidad);

    /** Convenience operation for future reservations: validation + commitment are one transaction. */
    default PrescriptionValidationResult validarYComprometer(long usuarioId, String codigoPrescripcion,
                                              long presentacionId, int cantidad, Instant instanteDecision) {
        var resultado=validar(usuarioId,codigoPrescripcion,presentacionId,cantidad,instanteDecision);
        resultado.exigirValida();
        comprometer(resultado.prescriptionId(),resultado.itemPrescripcionId(),presentacionId,cantidad);
        return resultado;
    }
}
