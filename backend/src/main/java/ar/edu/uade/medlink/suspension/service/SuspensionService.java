package ar.edu.uade.medlink.suspension.service;

import ar.edu.uade.medlink.common.ApiClock;
import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.common.PageRequests;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import ar.edu.uade.medlink.suspension.dto.SuspensionResponseDTO;
import ar.edu.uade.medlink.suspension.entity.Suspension;
import ar.edu.uade.medlink.suspension.repository.SuspensionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only administrative history; automatic evaluation remains in SuspensionPlanner. */
@Service
@Transactional(readOnly=true)
public class SuspensionService {
    private static final Set<String> SORTABLE = Set.of("fechaHoraInicio","id");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("fechaHoraInicio"),Sort.Order.desc("id"));
    private final SuspensionRepository suspensiones;
    private final Clock clock;

    public SuspensionService(SuspensionRepository suspensiones, Clock clock) {
        this.suspensiones=suspensiones;
        this.clock=clock;
    }

    public PageDTO<SuspensionResponseDTO> listar(long usuarioId, Boolean vigente,
            OffsetDateTime desde, OffsetDateTime hasta, int page, int size,
            List<String> sort, CurrentAccount actor) {
        if (actor==null || !"ADMINISTRADOR".equals(actor.rol())) throw ApiException.forbidden();
        if (usuarioId<=0) throw ApiException.validation("El identificador de Usuario debe ser positivo.");
        verificarInstante(desde);
        verificarInstante(hasta);
        if (desde!=null && hasta!=null && !desde.isBefore(hasta))
            throw ApiException.validation("desde debe ser anterior a hasta.");
        var pageable=PageRequests.of(page,size,sort,SORTABLE,DEFAULT_SORT);
        if (!suspensiones.existeUsuario(usuarioId)) throw ApiException.notFound();
        Instant t=clock.instant(); // CV-05: one instant for filters and DTOs.
        Specification<Suspension> filtro=(root,query,cb)-> {
            var conditions=new ArrayList<jakarta.persistence.criteria.Predicate>();
            conditions.add(cb.equal(root.get("usuarioId"),usuarioId));
            if (desde!=null) conditions.add(cb.greaterThanOrEqualTo(root.get("fechaHoraInicio"),desde.toInstant()));
            if (hasta!=null) conditions.add(cb.lessThan(root.get("fechaHoraInicio"),hasta.toInstant()));
            if (vigente!=null) {
                var active=cb.and(cb.lessThanOrEqualTo(root.get("fechaHoraInicio"),t),
                    cb.greaterThan(root.get("fechaHoraFin"),t));
                conditions.add(vigente ? active : cb.not(active));
            }
            return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return PageRequests.dto(suspensiones.findAll(filtro,pageable)
            .map(s->new SuspensionResponseDTO(s.getId(),ApiClock.timestamp(s.getFechaHoraInicio()),
                ApiClock.timestamp(s.getFechaHoraFin()),s.estaVigente(t),s.getMotivo().name())));
    }

    private static void verificarInstante(OffsetDateTime instante) {
        if (instante!=null && (instante.getNano()%1_000_000!=0 || instante.getYear()<1 || instante.getYear()>9999))
            throw ApiException.validation("Los instantes deben tener precisión de milisegundos.");
    }
}
