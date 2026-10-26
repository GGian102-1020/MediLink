package ar.edu.uade.medlink.prescripcion.service;

import ar.edu.uade.medlink.prescripcion.entity.ItemPrescripcion;

/**
 * RN-10/RN-12: decouples reservations from the prescription source so the MVP mock
 * can later be replaced by a real electronic-prescription integration (RNF-08).
 */
public interface PrescriptionValidationService {
    /**
     * Must run inside the caller's transaction, after the caller locked Stock (10.2).
     * Returns the locked item that authorizes {@code cantidad}; the caller then
     * invokes {@link ItemPrescripcion#comprometer(int)} in that same transaction.
     */
    ItemPrescripcion validar(long usuarioId, String codigoExterno, long presentacionId, int cantidad);
}
