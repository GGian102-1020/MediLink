package ar.edu.uade.medlink.prescripcion.service;

import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.prescripcion.entity.EstadoPrescripcion;
import ar.edu.uade.medlink.prescripcion.entity.ItemPrescripcion;
import ar.edu.uade.medlink.prescripcion.repository.*;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** MVP implementation backed by the simulated prescripcion/item_prescripcion tables. */
@Service
public class MockPrescriptionValidationService implements PrescriptionValidationService {
    private final PrescripcionRepository prescripciones;
    private final ItemPrescripcionRepository items;
    private final Clock clock;
    public MockPrescriptionValidationService(PrescripcionRepository prescripciones,
                                             ItemPrescripcionRepository items, Clock clock) {
        this.prescripciones=prescripciones; this.items=items; this.clock=clock;
    }
    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public ItemPrescripcion validar(long usuarioId, String codigoExterno, long presentacionId, int cantidad) {
        if (codigoExterno==null || codigoExterno.isBlank())
            throw ApiException.validation("La presentación requiere una prescripción.");
        if (cantidad<1) throw ApiException.validation("La cantidad debe ser mayor a cero.");
        // 10.2: Prescripción -> ItemPrescripción. Another user's code is reported as missing.
        var p=prescripciones.findLockedByCodigo(codigoExterno.strip()).orElse(null);
        if (p==null || p.getUsuarioId()!=usuarioId)
            throw ApiException.conflict("PRESCRIPTION_NOT_FOUND","La prescripción no existe o no pertenece al usuario.");
        if (p.getEstado()==EstadoPrescripcion.ANULADA)
            throw ApiException.conflict("PRESCRIPTION_ANNULLED","La prescripción está anulada.");
        if (!p.vigente(LocalDate.now(clock.withZone(ApiClock.ZONE))))
            throw ApiException.conflict("PRESCRIPTION_EXPIRED","La prescripción no está vigente.");
        var item=items.findLocked(p.getId(),presentacionId).orElseThrow(()->
            ApiException.conflict("PRESCRIPTION_NOT_AUTHORIZED","La prescripción no autoriza esta presentación."));
        if (cantidad>item.getCantidadDisponible())
            throw ApiException.conflict("PRESCRIPTION_QUANTITY_EXCEEDED","La receta no autoriza la cantidad solicitada.");
        return item;
    }
}
