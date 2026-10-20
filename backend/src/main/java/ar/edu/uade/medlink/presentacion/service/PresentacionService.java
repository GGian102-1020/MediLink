package ar.edu.uade.medlink.presentacion.service;

import ar.edu.uade.medlink.common.RequestValidation;
import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.PageRequests;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.medicamento.entity.*;
import ar.edu.uade.medlink.medicamento.repository.MedicamentoRepository;
import ar.edu.uade.medlink.medicamento.service.MedicamentoService;
import ar.edu.uade.medlink.presentacion.dto.*;
import ar.edu.uade.medlink.presentacion.entity.*;
import ar.edu.uade.medlink.presentacion.repository.PresentacionRepository;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated
@Transactional(readOnly=true)
public class PresentacionService {
    private final PresentacionRepository presentaciones;
    private final MedicamentoRepository medicamentos;
    private final EntityManager em;
    private final AuditService audit;
    public PresentacionService(PresentacionRepository presentaciones,MedicamentoRepository medicamentos,
                               EntityManager em,AuditService audit) {
        this.presentaciones=presentaciones; this.medicamentos=medicamentos;
        this.em=em; this.audit=audit;
    }
    private static Specification<PresentacionMedicamento> filter(Long medicamentoId,EstadoPresentacion estado,
                                                                 CondicionDispensa condicion) {
        return (root,query,cb)-> {
            var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (medicamentoId!=null) predicates.add(cb.equal(root.get("medicamento").get("id"),medicamentoId));
            if (estado!=null) predicates.add(cb.equal(root.get("estado"),estado));
            if (condicion!=null) predicates.add(cb.equal(root.get("condicionDispensa"),condicion));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
    // Sort names exposed by REST must be translated into the JPA association path.
    private static List<String> sort(List<String> specifications) {
        if(specifications==null) return null;
        return specifications.stream().map(spec->spec.startsWith("medicamentoId,")?
            "medicamento.id,"+spec.substring("medicamentoId,".length()):spec).toList();
    }
    public PageDTO<PresentacionDTO> listarPublico(long medicamentoId,int page,int size,List<String> sort) {
        requireActiveMedication(medicamentoId);
        return PageRequests.dto(presentaciones.findAll(filter(medicamentoId,EstadoPresentacion.ACTIVA,null),
            PageRequests.of(page,size,sort,Set.of("concentracionTexto","formaFarmaceutica","cantidadEnvase","id"),
                Sort.by("concentracionTexto","id"))).map(PresentacionService::dto));
    }
    public PresentacionDTO detallePublico(long id) {
        var p=presentaciones.findById(id).orElseThrow(ApiException::notFound);
        if(p.getEstado()!=EstadoPresentacion.ACTIVA || p.getMedicamento().getEstado()!=EstadoMedicamento.ACTIVO)
            throw ApiException.notFound();
        return dto(p);
    }
    public PageDTO<PresentacionDTO> listarAdmin(Long medicamentoId,String estado,String condicion,int page,int size,
                                                List<String> sort,CurrentAccount actor) {
        MedicamentoService.admin(actor);
        EstadoPresentacion parsedStatus=null;
        CondicionDispensa parsedDispensa=null;
        try {
            if(estado!=null) parsedStatus=EstadoPresentacion.valueOf(estado);
            if(condicion!=null) parsedDispensa=CondicionDispensa.valueOf(condicion);
        } catch (IllegalArgumentException ex) { throw ApiException.validation("Filtros de presentación inválidos."); }
        return PageRequests.dto(presentaciones.findAll(filter(medicamentoId,parsedStatus,parsedDispensa),
            PageRequests.of(page,size,sort(sort),Set.of("medicamento.id","concentracionTexto","formaFarmaceutica","id"),
                Sort.by("medicamento.id","id"))).map(PresentacionService::dto));
    }
    public PresentacionDTO detalleAdmin(long id,CurrentAccount actor) {
        MedicamentoService.admin(actor);
        return dto(presentaciones.findById(id).orElseThrow(ApiException::notFound));
    }
    @Transactional
    public PresentacionDTO crear(@Valid PresentacionCreateRequest req,CurrentAccount actor,HttpServletRequest request) {
        MedicamentoService.admin(actor);
        if (req.medicamentoId()==null || req.cantidadEnvase()==null || req.cantidadEnvase()<1
            || req.condicionDispensa()==null) throw ApiException.validation("Datos de presentación inválidos.");
        // Lock the parent before checking the active unique index. A new
        // presentation may belong to an inactive medication: public searches
        // still hide both until the parent is active (RN-06, RN-10, RN-45).
        var m=medicamentos.findLocked(req.medicamentoId()).orElseThrow(ApiException::notFound);
        String c=normalize(req.concentracionTexto());
        String f=normalize(req.formaFarmaceutica());
        String u=normalize(req.unidadExpendio());
        ensureNoActiveDuplicate(m.getId(),c,f,req.cantidadEnvase(),u,req.condicionDispensa(),null);
        PresentacionMedicamento p;
        try {
            p=presentaciones.saveAndFlush(new PresentacionMedicamento(m,c,f,req.cantidadEnvase(),u,req.condicionDispensa()));
        } catch (DataIntegrityViolationException ex) {
            throw translatedDuplicate(ex);
        }
        audit.record(actor.id(),actor.rol(),"CREAR_PRESENTACION","PRESENTACION",p.getId(),null,null,null,summary(p),request);
        return dto(p);
    }
    @Transactional
    public PresentacionDTO cambiarEstado(long id,boolean activa,String motivo,CurrentAccount actor,HttpServletRequest request) {
        MedicamentoService.admin(actor);
        String reason=RequestValidation.requiredReason(motivo);
        // Find only the parent identifier without locking the child first (10.2).
        @SuppressWarnings("unchecked")
        List<Number> ids=em.createNativeQuery("SELECT id_medicamento FROM presentacion_medicamento WHERE id_presentacion=:id")
            .setParameter("id",id).getResultList();
        if(ids.isEmpty()) throw ApiException.notFound();
        long medicineId=ids.getFirst().longValue();
        var medicine=medicamentos.findLocked(medicineId).orElseThrow(ApiException::notFound);
        var p=presentaciones.findLocked(id).orElseThrow(ApiException::notFound);
        var next=activa?EstadoPresentacion.ACTIVA:EstadoPresentacion.INACTIVA;
        if(p.getEstado()==next) throw ApiException.conflict("INVALID_STATE_TRANSITION","La presentación ya está en ese estado.");
        if(activa) ensureNoActiveDuplicate(medicine.getId(),p.getConcentracionTexto(),p.getFormaFarmaceutica(),
            p.getCantidadEnvase(),p.getUnidadExpendio(),p.getCondicionDispensa(),id);
        var before=summary(p);
        p.cambiarEstado(next);
        try {
            // Flush before the audit so a conflicting active duplicate returns 409,
            // not a generic transaction-commit failure after method return.
            presentaciones.flush();
        } catch (DataIntegrityViolationException ex) {
            throw translatedDuplicate(ex);
        }
        audit.record(actor.id(),actor.rol(),activa?"REACTIVAR_PRESENTACION":"DESACTIVAR_PRESENTACION",
            "PRESENTACION",id,null,reason,before,summary(p),request);
        return dto(p);
    }
    private Medicamento requireActiveMedication(long id) {
        var m=medicamentos.findById(id).orElseThrow(ApiException::notFound);
        if(m.getEstado()!=EstadoMedicamento.ACTIVO) throw ApiException.notFound();
        return m;
    }
    public static String normalize(String text) {
        if (text==null) throw ApiException.validation("Texto estructural de presentación inválido.");
        String norm=text.strip().replaceAll("\\s+"," ");
        if(norm.isEmpty() || norm.length()>80) throw ApiException.validation("Texto estructural de presentación inválido.");
        return norm;
    }
    // SQL uses the same comparison as ux_presentacion_activa_identidad; we lock
    // the parent medication first, so duplicate attempts serialize predictably.
    private void ensureNoActiveDuplicate(long med,String c,String f,int count,String unit,CondicionDispensa kind,Long exclude) {
        @SuppressWarnings("unchecked")
        List<Number> matches=em.createNativeQuery("""
            SELECT id_presentacion FROM presentacion_medicamento
            WHERE id_medicamento=:med AND lower(concentracion_texto)=lower(:con)
            AND lower(forma_farmaceutica)=lower(:forma) AND cantidad_envase=:cnt
            AND lower(unidad_expendio)=lower(:uni) AND condicion_dispensa=CAST(:kind AS condicion_dispensa)
            AND estado='ACTIVA'
            """).setParameter("med",med).setParameter("con",c).setParameter("forma",f)
            .setParameter("cnt",count).setParameter("uni",unit).setParameter("kind",kind.name()).getResultList();
        if(matches.stream().anyMatch(x->exclude==null || x.longValue()!=exclude))
            throw ApiException.conflict("PRESENTATION_DUPLICATE","Ya existe una presentación activa con esa estructura.");
    }
    private static RuntimeException translatedDuplicate(DataIntegrityViolationException ex) {
        // Only the catalog's active-identity index maps to PRESENTATION_DUPLICATE.
        // Unexpected constraints remain errors to be diagnosed, never fake 409s.
        Throwable current=ex;
        while (current!=null) {
            if (current.getMessage()!=null && current.getMessage().contains("ux_presentacion_activa_identidad"))
                return ApiException.conflict("PRESENTATION_DUPLICATE","Ya existe una presentación activa con esa estructura.");
            current=current.getCause();
        }
        return ex;
    }
    public static PresentacionDTO dto(PresentacionMedicamento p) {
        return new PresentacionDTO(p.getId(),p.getMedicamento().getId(),p.getMedicamento().getNombre(),
            p.getConcentracionTexto(),p.getFormaFarmaceutica(),p.getCantidadEnvase(),p.getUnidadExpendio(),
            p.getCondicionDispensa(),p.getEstado());
    }
    private static Map<String,Object> summary(PresentacionMedicamento p) {
        return Map.of("medicamentoId",p.getMedicamento().getId(),"concentracionTexto",p.getConcentracionTexto(),
            "formaFarmaceutica",p.getFormaFarmaceutica(),"cantidadEnvase",p.getCantidadEnvase(),
            "unidadExpendio",p.getUnidadExpendio(),"condicionDispensa",p.getCondicionDispensa().name(),"estado",p.getEstado().name());
    }
}
