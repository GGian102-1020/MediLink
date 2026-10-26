package ar.edu.uade.medlink.prescripcion.service;

import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.prescripcion.entity.EstadoPrescripcion;
import ar.edu.uade.medlink.prescripcion.repository.ItemPrescripcionRepository;
import ar.edu.uade.medlink.prescripcion.repository.PrescripcionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.uade.medlink.prescripcion.service.PrescriptionValidationResult.Status;

/** Local simulated implementation; all locks remain held until the calling transaction commits. */
@Service
public class MockPrescriptionValidationService implements PrescriptionValidationService {
    private final PrescripcionRepository prescripciones;
    private final ItemPrescripcionRepository items;

    public MockPrescriptionValidationService(PrescripcionRepository prescripciones,
                                             ItemPrescripcionRepository items) {
        this.prescripciones=prescripciones;
        this.items=items;
    }

    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public PrescriptionValidationResult validar(long usuarioId, String codigoPrescripcion, long presentacionId,
                                                 int cantidad, Instant instanteDecision) {
        if (codigoPrescripcion==null || codigoPrescripcion.isBlank())
            throw ApiException.validation("La presentación requiere una prescripción.");
        if (cantidad<1) throw ApiException.validation("La cantidad debe ser mayor a cero.");
        Objects.requireNonNull(instanteDecision,"Se requiere el instante de decisión T.");
        LocalDate hoy=LocalDate.ofInstant(instanteDecision,ApiClock.ZONE);

        // Canonical order: Prescripción, then ItemPrescripción. Do not expose foreign codes (13.1).
        var p=prescripciones.findLockedByCodigo(codigoPrescripcion.strip()).orElse(null);
        if (p==null) return PrescriptionValidationResult.rechazo(Status.NOT_FOUND);
        if (p.getUsuarioId()!=usuarioId) return PrescriptionValidationResult.rechazo(Status.NOT_OWNER);
        if (p.getEstado()==EstadoPrescripcion.ANULADA)
            return PrescriptionValidationResult.rechazo(Status.PRESCRIPTION_ANNULLED);
        if (hoy.isBefore(p.getFechaEmision()))
            return PrescriptionValidationResult.rechazo(Status.NOT_YET_VALID);
        if (hoy.isAfter(p.getFechaVencimiento()))
            return PrescriptionValidationResult.rechazo(Status.EXPIRED);

        var item=items.findLocked(p.getId(),presentacionId).orElse(null);
        if (item==null) return PrescriptionValidationResult.rechazo(Status.MEDICATION_NOT_AUTHORIZED);
        if (cantidad>item.getCantidadDisponible())
            return PrescriptionValidationResult.rechazo(Status.INSUFFICIENT_QUANTITY);
        return new PrescriptionValidationResult(Status.VALID,p.getId(),item.getId(),presentacionId,
            item.getCantidadAutorizada(),item.getCantidadComprometida(),item.getCantidadUtilizada(),
            item.getCantidadDisponible(),p.getFechaVencimiento());
    }

    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public void comprometer(long prescriptionId, long itemPrescripcionId, long presentacionId, int cantidad) {
        // Also safe when called independently: acquire Prescripción before Item.
        prescripciones.findLocked(prescriptionId).orElseThrow(ApiException::notFound);
        var item=items.findLocked(prescriptionId,presentacionId).orElseThrow(ApiException::notFound);
        if (!Objects.equals(item.getId(),itemPrescripcionId)) throw ApiException.notFound();
        item.comprometer(cantidad);  // DB CHECK enforces the same invariant.
    }
}
