package ar.edu.uade.medlink.farmacia.service;

import ar.edu.uade.medlink.admin.AdminDtos.PageDTO;
import ar.edu.uade.medlink.admin.AdministrationService;
import ar.edu.uade.medlink.common.*;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.farmacia.dto.*;
import ar.edu.uade.medlink.farmacia.entity.*;
import ar.edu.uade.medlink.farmacia.repository.*;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated
@Transactional(readOnly=true)
public class FarmaciaService {
    private final FarmaciaRepository farmacias;
    private final FarmaciaIntegration integration;
    private final AuditService audit;
    private final Clock clock;
    public FarmaciaService(FarmaciaRepository farmacias, FarmaciaIntegration integration,
                           AuditService audit, Clock clock) {
        this.farmacias=farmacias; this.integration=integration; this.audit=audit; this.clock=clock;
    }
    public PageDTO<FarmaciaDTO> listar(String nombre, String estado, int page, int size,
                                       List<String> sort, CurrentAccount actor) {
        requireAdmin(actor);
        EstadoFarmacia status=null;
        if (estado!=null) {
            try { status=EstadoFarmacia.valueOf(estado); }
            catch (IllegalArgumentException ex) { throw ApiException.validation("Estado de Farmacia inválido."); }
        }
        var pageable=PageRequests.of(page,size,sort,Set.of("nombre","estado","id"),Sort.by("nombre","id"));
        final EstadoFarmacia filter=status;
        Specification<Farmacia> spec=(root,query,cb)-> {
            var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (nombre!=null && !nombre.isBlank()) {
                String value=nombre.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_");
                predicates.add(cb.like(cb.lower(root.get("nombre")),"%"+value+"%",'!'));
            }
            if (filter!=null) predicates.add(cb.equal(root.get("estado"),filter));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result=farmacias.findAll(spec,pageable);
        Set<Long> ids=new HashSet<>();
        result.getContent().forEach(f->ids.add(f.getId()));
        Set<Long> operativas=integration.farmaciasConOperadorActivo(ids);
        return PageRequests.dto(result.map(f->dto(f,operativas.contains(f.getId()))));
    }
    @Transactional
    public FarmaciaDTO crear(@Valid FarmaciaRequest req, CurrentAccount actor, HttpServletRequest request) {
        requireAdmin(actor);
        Farmacia f=farmacias.saveAndFlush(new Farmacia(req.nombre(),req.direccion(),req.telefono(),req.emailContacto()));
        audit.record(actor.id(),actor.rol(),"CREAR_FARMACIA","FARMACIA",f.getId(),null,null,null,summary(f),request);
        return dto(f);
    }
    @Transactional
    public FarmaciaDTO editar(long id, @Valid FarmaciaRequest req, CurrentAccount actor, HttpServletRequest request) {
        requireAdmin(actor);
        Farmacia f=farmacias.findLocked(id).orElseThrow(ApiException::notFound);
        var before=summary(f);
        f.editar(req.nombre(),req.direccion(),req.telefono(),req.emailContacto());
        audit.record(actor.id(),actor.rol(),"EDITAR_FARMACIA","FARMACIA",id,null,null,before,summary(f),request);
        return dto(f);
    }
    @Transactional
    public FarmaciaDTO cambiarEstado(long id, boolean activa, String motivo, CurrentAccount actor, HttpServletRequest request) {
        var t=clock.instant();
        requireAdmin(actor);
        String reason=AdministrationService.requiredReason(motivo);
        Farmacia f=farmacias.findLocked(id).orElseThrow(ApiException::notFound);
        EstadoFarmacia next=activa?EstadoFarmacia.ACTIVA:EstadoFarmacia.INACTIVA;
        if (f.getEstado()==next) throw ApiException.conflict("INVALID_STATE_TRANSITION","La farmacia ya está en ese estado.");
        if (!activa && integration.tieneReservasActivas(id,t))
            throw ApiException.conflict("PHARMACY_HAS_ACTIVE_RESERVATIONS","La farmacia tiene reservas activas.");
        var before=summary(f);
        f.cambiarEstado(next);
        audit.record(actor.id(),actor.rol(),activa?"REACTIVAR_FARMACIA":"DESACTIVAR_FARMACIA",
            "FARMACIA",id,null,reason,before,summary(f),request);
        return dto(f);
    }
    private FarmaciaDTO dto(Farmacia f) {
        return dto(f,f.getEstado()==EstadoFarmacia.ACTIVA && integration.tieneOperadorActivo(f.getId()));
    }
    private static FarmaciaDTO dto(Farmacia f,boolean operativa) {
        return new FarmaciaDTO(f.getId(),f.getNombre(),f.getDireccion(),f.getTelefono(),f.getEmailContacto(),
            f.getEstado(),f.getEstado()==EstadoFarmacia.ACTIVA && operativa);
    }
    private static Map<String,Object> summary(Farmacia f) {
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("nombre",f.getNombre()); data.put("direccion",f.getDireccion());
        data.put("telefono",f.getTelefono()); data.put("emailContacto",f.getEmailContacto());
        data.put("estado",f.getEstado().name()); return data;
    }
    private static void requireAdmin(CurrentAccount actor) {
        if (actor==null || !"ADMINISTRADOR".equals(actor.rol())) throw ApiException.forbidden();
    }
}
