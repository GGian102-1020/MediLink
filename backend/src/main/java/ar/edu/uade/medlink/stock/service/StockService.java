package ar.edu.uade.medlink.stock.service;

import ar.edu.uade.medlink.common.*;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.farmacia.entity.EstadoFarmacia;
import ar.edu.uade.medlink.farmacia.repository.*;
import ar.edu.uade.medlink.security.CurrentAccount;
import ar.edu.uade.medlink.stock.dto.*;
import ar.edu.uade.medlink.stock.entity.*;
import ar.edu.uade.medlink.stock.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service @Validated
@Transactional(readOnly=true)
public class StockService {
    private final StockRepository stocks;
    private final MovimientoStockRepository movimientos;
    private final FarmaciaRepository farmacias;
    private final FarmaciaIntegration farmaciasIntegration;
    private final StockIntegration integration;
    private final AuditService audit;
    private final Clock clock;
    public StockService(StockRepository stocks, MovimientoStockRepository movimientos,
                        FarmaciaRepository farmacias, FarmaciaIntegration farmaciasIntegration,
                        StockIntegration integration, AuditService audit, Clock clock) {
        this.stocks=stocks; this.movimientos=movimientos; this.farmacias=farmacias;
        this.farmaciasIntegration=farmaciasIntegration; this.integration=integration;
        this.audit=audit; this.clock=clock;
    }
    public PageDTO<StockDTO> listar(long farmacia, int page, int size, List<String> sort, CurrentAccount actor) {
        checkRead(farmacia,actor);
        var pageable=PageRequests.of(page,size,sort,Set.of("presentacionId","cantidadDisponible","fechaHoraActualizacion","id"),
            Sort.by("presentacionId","id"));
        return PageRequests.dto(stocks.findAll((root,q,cb)->cb.equal(root.get("farmaciaId"),farmacia),pageable).map(StockService::dto));
    }
    public PageDTO<MovimientoStockDTO> movimientos(long farmacia, Long presentacion, OffsetDateTime desde,
        OffsetDateTime hasta, int page, int size, List<String> sort, CurrentAccount actor) {
        checkRead(farmacia,actor);
        if (desde!=null && hasta!=null && !desde.isBefore(hasta)) throw ApiException.validation("desde debe ser anterior a hasta.");
        var pageable=PageRequests.of(page,size,sort,Set.of("fechaHora","id"),
            Sort.by(Sort.Order.desc("fechaHora"),Sort.Order.desc("id")));
        Specification<MovimientoStock> spec=(root,q,cb)-> {
            var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("stock").get("farmaciaId"),farmacia));
            if (presentacion!=null) predicates.add(cb.equal(root.get("stock").get("presentacionId"),presentacion));
            if (desde!=null) predicates.add(cb.greaterThanOrEqualTo(root.get("fechaHora"),desde.toInstant()));
            if (hasta!=null) predicates.add(cb.lessThan(root.get("fechaHora"),hasta.toInstant()));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result=movimientos.findAll(spec,pageable);
        var codes=integration.codigosReservas(result.getContent().stream().map(MovimientoStock::getReservaId).filter(Objects::nonNull).distinct().toList());
        return PageRequests.dto(result.map(m->new MovimientoStockDTO(m.getId(),m.getStock().getId(),m.getTipo(),
            m.getCantidadAnterior(),m.getCantidadNueva(),m.getDelta(),ApiClock.timestamp(m.getFechaHora()),
            m.getReservaId()==null?null:codes.get(m.getReservaId()),m.getActorCuentaId(),m.getMotivo())));
    }
    @Transactional
    public StockDTO ajustar(long farmacia, long presentacion, @Valid AjusteStockRequest req,
                            CurrentAccount actor, HttpServletRequest request) {
        Instant t=clock.instant().truncatedTo(ChronoUnit.MILLIS);
        integration.autorizar(farmacia,actor);
        if (req.cantidadDisponible()==null || req.cantidadDisponible()<0)
            throw ApiException.validation("La cantidad disponible debe ser un entero no negativo.");
        String reason=RequestValidation.requiredReason(req.motivo());
        // 10.2: Cuenta Usuario -> Usuario -> Farmacia -> Medicamento -> Presentación -> Stock
        // -> Prescripción -> ItemPrescripción -> Reserva. T remains fixed across lock waits.
        var pending=integration.bloquearUsuariosVencidos(farmacia,presentacion,t);
        var f=farmacias.findLocked(farmacia).orElseThrow(ApiException::notFound);
        integration.autorizar(farmacia,actor);
        if (f.getEstado()!=EstadoFarmacia.ACTIVA) throw ApiException.conflict("PHARMACY_INACTIVE","La farmacia está inactiva.");
        if ("OPERADOR".equals(actor.rol()) && !farmaciasIntegration.tieneOperadorActivo(farmacia))
            throw ApiException.conflict("PHARMACY_NOT_OPERATIONAL","La farmacia no es operativa.");
        integration.bloquearCatalogoActivo(presentacion);
        Stock stock=stocks.findLocked(farmacia,presentacion).orElse(null);
        integration.materializarVencimientos(farmacia,presentacion,t,stock,pending,request);
        boolean created=stock==null;
        if (created) stock=stocks.saveAndFlush(new Stock(farmacia,presentacion,t));
        int before=stock.getCantidadDisponible();
        stock.establecerCantidad(req.cantidadDisponible(),t);
        movimientos.saveAndFlush(new MovimientoStock(stock,null,TipoMovimientoStock.AJUSTE_MANUAL,
            before,req.cantidadDisponible(),t,actor.id(),reason));
        audit.record(actor.id(),actor.rol(),"AJUSTAR_STOCK","STOCK",stock.getId(),null,reason,
            created?null:Map.of("cantidadDisponible",before),
            Map.of("farmaciaId",farmacia,"presentacionId",presentacion,"cantidadDisponible",req.cantidadDisponible()),request);
        return dto(stock);
    }
    public List<DisponibilidadFarmaciaDTO> disponibilidad(long presentacion) {
        return integration.disponibilidad(presentacion);
    }
    private void checkRead(long farmacia, CurrentAccount actor) {
        integration.autorizar(farmacia,actor);
        if (!farmacias.existsById(farmacia)) throw ApiException.notFound();
    }
    private static StockDTO dto(Stock s) {
        return new StockDTO(s.getFarmaciaId(),s.getPresentacionId(),s.getCantidadDisponible(),ApiClock.timestamp(s.getFechaHoraActualizacion()));
    }
}
