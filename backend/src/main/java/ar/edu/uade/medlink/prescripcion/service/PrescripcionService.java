package ar.edu.uade.medlink.prescripcion.service;

import ar.edu.uade.medlink.common.*;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.medicamento.service.MedicamentoService;
import ar.edu.uade.medlink.prescripcion.dto.*;
import ar.edu.uade.medlink.prescripcion.entity.*;
import ar.edu.uade.medlink.prescripcion.repository.PrescripcionRepository;
import ar.edu.uade.medlink.presentacion.entity.CondicionDispensa;
import ar.edu.uade.medlink.presentacion.entity.PresentacionMedicamento;
import ar.edu.uade.medlink.presentacion.repository.PresentacionRepository;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/** Administrative management of simulated prescriptions (RN-25, RN-30). */
@Service @Validated
@Transactional(readOnly=true)
public class PrescripcionService {
    private static final Set<String> SORTABLE=Set.of("fechaEmision","fechaVencimiento","codigoExterno","id");
    private static final Sort DEFAULT_SORT=Sort.by(Sort.Order.desc("fechaEmision"),Sort.Order.desc("id"));
    private final PrescripcionRepository prescripciones;
    private final PresentacionRepository presentaciones;
    private final AuditService audit;
    private final Clock clock;
    public PrescripcionService(PrescripcionRepository prescripciones, PresentacionRepository presentaciones,
                               AuditService audit, Clock clock) {
        this.prescripciones=prescripciones; this.presentaciones=presentaciones;
        this.audit=audit; this.clock=clock;
    }
    private static Specification<Prescripcion> filter(Long usuarioId, EstadoPrescripcion estado, String codigoExterno) {
        return (root,query,cb)-> {
            var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (usuarioId!=null) predicates.add(cb.equal(root.get("usuarioId"),usuarioId));
            if (estado!=null) predicates.add(cb.equal(root.get("estado"),estado));
            if (codigoExterno!=null && !codigoExterno.isBlank()) predicates.add(cb.equal(root.get("codigoExterno"),codigoExterno.strip()));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
    public PageDTO<PrescripcionDTO> listarAdmin(Long usuarioId, String estado, String codigoExterno, int page, int size,
                                                List<String> sort, CurrentAccount actor) {
        MedicamentoService.admin(actor);
        EstadoPrescripcion parsed=null;
        if (estado!=null) {
            try { parsed=EstadoPrescripcion.valueOf(estado); }
            catch (IllegalArgumentException ex) { throw ApiException.validation("Estado de prescripción inválido."); }
        }
        return PageRequests.dto(prescripciones.findAll(filter(usuarioId,parsed,codigoExterno),
            PageRequests.of(page,size,sort,SORTABLE,DEFAULT_SORT)).map(PrescripcionService::dto));
    }
    public PrescripcionDTO detalleAdmin(long id, CurrentAccount actor) {
        MedicamentoService.admin(actor);
        return dto(prescripciones.findById(id).orElseThrow(ApiException::notFound));
    }
    @Transactional
    public PrescripcionDTO crear(@Valid PrescripcionCreateRequest req, CurrentAccount actor, HttpServletRequest request) {
        MedicamentoService.admin(actor);
        if (req.codigoExterno()==null || req.codigoExterno().isEmpty() || req.codigoExterno().length()>80
            || req.usuarioId()==null || req.fechaEmision()==null || req.fechaVencimiento()==null
            || req.items()==null || req.items().isEmpty())
            throw ApiException.validation("Datos de prescripción inválidos.");
        if (req.fechaEmision().isAfter(req.fechaVencimiento()))
            throw ApiException.validation("La fecha de emisión no puede ser posterior a la de vencimiento.");
        var seen=new HashSet<Long>();
        for (var item:req.items()) {
            if (item==null || item.presentacionId()==null || item.cantidadAutorizada()==null || item.cantidadAutorizada()<1)
                throw ApiException.validation("Ítem de prescripción inválido.");
            if (!seen.add(item.presentacionId()))
                throw ApiException.validation("Una prescripción no puede repetir la misma presentación.");
        }
        if (!prescripciones.existeUsuario(req.usuarioId())) throw ApiException.notFound();
        if (prescripciones.existsByCodigoExterno(req.codigoExterno())) throw duplicate();
        var p=new Prescripcion(req.codigoExterno(),req.usuarioId(),req.fechaEmision(),req.fechaVencimiento(),
            clock.instant().truncatedTo(ChronoUnit.MILLIS));
        for (var item:req.items()) {
            PresentacionMedicamento presentacion=presentaciones.findById(item.presentacionId()).orElseThrow(ApiException::notFound);
            // The schema only links BAJO_RECETA reservations to prescription items.
            if (presentacion.getCondicionDispensa()!=CondicionDispensa.BAJO_RECETA)
                throw ApiException.validation("Solo se pueden prescribir presentaciones BAJO_RECETA.");
            p.agregarItem(presentacion,item.cantidadAutorizada());
        }
        try {
            p=prescripciones.saveAndFlush(p);
        } catch (DataIntegrityViolationException ex) {
            throw translatedDuplicate(ex);
        }
        audit.record(actor.id(),actor.rol(),"CREAR_PRESCRIPCION","PRESCRIPCION",p.getId(),p.getUsuarioId(),
            null,null,summary(p),request);
        return dto(p);
    }
    // Annulment does not invalidate reservations created earlier (schema section 11).
    @Transactional
    public PrescripcionDTO anular(long id, String motivo, CurrentAccount actor, HttpServletRequest request) {
        MedicamentoService.admin(actor);
        String reason=RequestValidation.requiredReason(motivo);
        var p=prescripciones.findLocked(id).orElseThrow(ApiException::notFound);
        if (p.getEstado()==EstadoPrescripcion.ANULADA)
            throw ApiException.conflict("INVALID_STATE_TRANSITION","La prescripción ya está anulada.");
        var before=summary(p);
        p.anular();
        audit.record(actor.id(),actor.rol(),"ANULAR_PRESCRIPCION","PRESCRIPCION",id,p.getUsuarioId(),
            reason,before,summary(p),request);
        return dto(p);
    }
    private static ApiException duplicate() {
        return ApiException.conflict("PRESCRIPTION_CODE_ALREADY_EXISTS","Ya existe una prescripción con ese código externo.");
    }
    private static RuntimeException translatedDuplicate(DataIntegrityViolationException ex) {
        // Only the unique external code maps to PRESCRIPTION_CODE_ALREADY_EXISTS; other violations stay visible.
        for (Throwable current=ex; current!=null; current=current.getCause()) {
            if (current.getMessage()!=null && current.getMessage().contains("prescripcion_codigo_externo_key"))
                return duplicate();
        }
        return ex;
    }
    public static PrescripcionDTO dto(Prescripcion p) {
        return new PrescripcionDTO(p.getId(),p.getCodigoExterno(),p.getUsuarioId(),p.getFechaEmision(),
            p.getFechaVencimiento(),p.getEstado(),p.getItems().stream().map(PrescripcionService::dto).toList());
    }
    public static ItemPrescripcionDTO dto(ItemPrescripcion i) {
        var pr=i.getPresentacion();
        return new ItemPrescripcionDTO(i.getId(),pr.getId(),
            pr.getConcentracionTexto()+" - "+pr.getFormaFarmaceutica()+" - "+pr.getUnidadExpendio()+" x "+pr.getCantidadEnvase(),
            i.getCantidadAutorizada(),i.getCantidadComprometida(),i.getCantidadUtilizada(),i.getCantidadDisponible());
    }
    private static Map<String,Object> summary(Prescripcion p) {
        return Map.of("codigoExterno",p.getCodigoExterno(),"usuarioId",p.getUsuarioId(),
            "fechaEmision",p.getFechaEmision().toString(),"fechaVencimiento",p.getFechaVencimiento().toString(),
            "estado",p.getEstado().name(),"cantidadItems",p.getItems().size());
    }
}
