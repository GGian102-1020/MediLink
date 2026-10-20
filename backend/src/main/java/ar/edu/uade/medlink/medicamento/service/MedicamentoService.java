package ar.edu.uade.medlink.medicamento.service;

import ar.edu.uade.medlink.common.RequestValidation;
import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.admin.AdminDtos.CambioEstadoRequest;
import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.PageRequests;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.medicamento.dto.*;
import ar.edu.uade.medlink.medicamento.entity.*;
import ar.edu.uade.medlink.medicamento.repository.MedicamentoRepository;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated
@Transactional(readOnly=true)
public class MedicamentoService {
    private final MedicamentoRepository medicamentos;
    private final AuditService audit;
    public MedicamentoService(MedicamentoRepository medicamentos, AuditService audit) {
        this.medicamentos=medicamentos; this.audit=audit;
    }
    // Search values are bound parameters. Escape SQL LIKE wildcards supplied as literal text.
    public static String pattern(String raw) {
        if (raw==null || raw.isBlank()) return "%";
        return "%"+raw.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
    }
    public static Specification<Medicamento> filter(String nombre, EstadoMedicamento estado) {
        return (root,query,cb)-> {
            var conditions=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (estado!=null) conditions.add(cb.equal(root.get("estado"),estado));
            if (nombre!=null && !nombre.isBlank()) {
                var normalized=cb.function("unaccent",String.class,cb.lower(root.get("nombre")));
                var value=cb.function("unaccent",String.class,cb.literal(pattern(nombre)));
                conditions.add(cb.like(normalized,value,'!'));
            }
            return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
    public PageDTO<MedicamentoDTO> listarPublico(String nombre,int page,int size,List<String> sort) {
        return PageRequests.dto(medicamentos.findAll(filter(nombre,EstadoMedicamento.ACTIVO),
            PageRequests.of(page,size,sort,Set.of("nombre","id"),Sort.by("nombre","id"))).map(MedicamentoService::dto));
    }
    public PageDTO<MedicamentoDTO> listarAdmin(String nombre,String estado,int page,int size,List<String> sort,CurrentAccount actor) {
        admin(actor);
        EstadoMedicamento value=null;
        if (estado!=null) {
            try { value=EstadoMedicamento.valueOf(estado); }
            catch (IllegalArgumentException ex) { throw ApiException.validation("Estado de medicamento inválido."); }
        }
        return PageRequests.dto(medicamentos.findAll(filter(nombre,value),
            PageRequests.of(page,size,sort,Set.of("nombre","estado","id"),Sort.by("nombre","id"))).map(MedicamentoService::dto));
    }
    @Transactional
    public MedicamentoDTO crear(@Valid MedicamentoRequest req,CurrentAccount actor,HttpServletRequest request) {
        admin(actor);
        var m=medicamentos.saveAndFlush(new Medicamento(nombre(req.nombre())));
        audit.record(actor.id(),actor.rol(),"CREAR_MEDICAMENTO","MEDICAMENTO",m.getId(),null,null,null,resumen(m),request);
        return dto(m);
    }
    @Transactional
    public MedicamentoDTO editar(long id,@Valid MedicamentoRequest req,CurrentAccount actor,HttpServletRequest request) {
        admin(actor);
        var m=medicamentos.findLocked(id).orElseThrow(ApiException::notFound);
        var before=resumen(m);
        m.renombrar(nombre(req.nombre()));
        audit.record(actor.id(),actor.rol(),"EDITAR_MEDICAMENTO","MEDICAMENTO",id,null,null,before,resumen(m),request);
        return dto(m);
    }
    @Transactional
    public MedicamentoDTO cambiarEstado(long id,boolean activo,String motivo,CurrentAccount actor,HttpServletRequest request) {
        admin(actor);
        String reason=RequestValidation.requiredReason(motivo);
        var m=medicamentos.findLocked(id).orElseThrow(ApiException::notFound);
        var next=activo?EstadoMedicamento.ACTIVO:EstadoMedicamento.INACTIVO;
        if(m.getEstado()==next) throw ApiException.conflict("INVALID_STATE_TRANSITION","El medicamento ya está en ese estado.");
        var before=resumen(m);
        m.cambiarEstado(next);
        audit.record(actor.id(),actor.rol(),activo?"REACTIVAR_MEDICAMENTO":"DESACTIVAR_MEDICAMENTO",
            "MEDICAMENTO",id,null,reason,before,resumen(m),request);
        return dto(m);
    }
    public static void admin(CurrentAccount actor) {
        if (actor==null || !"ADMINISTRADOR".equals(actor.rol())) throw ApiException.forbidden();
    }
    private static String nombre(String raw) {
        if (raw==null || raw.strip().isEmpty() || raw.strip().length()>160) throw ApiException.validation("Nombre de medicamento inválido.");
        return raw.strip();
    }
    public static MedicamentoDTO dto(Medicamento m) { return new MedicamentoDTO(m.getId(),m.getNombre(),m.getEstado()); }
    private static Map<String,Object> resumen(Medicamento m) {
        return Map.of("nombre",m.getNombre(),"estado",m.getEstado().name());
    }
}
