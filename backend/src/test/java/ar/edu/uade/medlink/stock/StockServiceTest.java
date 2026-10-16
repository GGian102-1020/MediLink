package ar.edu.uade.medlink.stock;

import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.farmacia.entity.*;
import ar.edu.uade.medlink.farmacia.repository.*;
import ar.edu.uade.medlink.security.CurrentAccount;
import ar.edu.uade.medlink.stock.dto.*;
import ar.edu.uade.medlink.stock.entity.*;
import ar.edu.uade.medlink.stock.repository.*;
import ar.edu.uade.medlink.stock.service.StockService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {
    @Mock StockRepository stocks;
    @Mock MovimientoStockRepository movimientos;
    @Mock FarmaciaRepository farmacias;
    @Mock FarmaciaIntegration pharmacyIntegration;
    @Mock StockIntegration integration;
    @Mock AuditService audit;
    StockService service;
    final Instant t=Instant.parse("2026-10-16T14:00:00.123Z");
    final CurrentAccount admin=new CurrentAccount(1,"ADMINISTRADOR","test",0);
    final CurrentAccount operator=new CurrentAccount(2,"OPERADOR","test",0);
    @BeforeEach void setUp() {
        service=new StockService(stocks,movimientos,farmacias,pharmacyIntegration,integration,audit,Clock.fixed(t,ZoneOffset.UTC));
    }
    Farmacia farmacia() { return new Farmacia("Central","Calle",null,null); }
    Stock stock(int amount) {
        var s=new Stock(4,3,t);s.establecerCantidad(amount,t);ReflectionTestUtils.setField(s,"id",8L);return s;
    }
    void ready(Stock stock) {
        when(farmacias.findLocked(4)).thenReturn(Optional.of(farmacia()));
        when(stocks.findLocked(4,3)).thenReturn(Optional.of(stock));
    }
    @Test void adjustmentIsAbsoluteAndHasCorrectMovementAndAudit() {
        var s=stock(10);ready(s);
        var dto=service.ajustar(4,3,new AjusteStockRequest(5," Conteo "),admin,null);
        assertEquals(5,dto.cantidadDisponible()); assertEquals("2026-10-16T11:00:00.123-03:00",dto.fechaHoraActualizacion());
        var movement=ArgumentCaptor.forClass(MovimientoStock.class);
        verify(movimientos).saveAndFlush(movement.capture());
        var m=movement.getValue();assertEquals(10,m.getCantidadAnterior());assertEquals(5,m.getCantidadNueva());
        assertEquals(-5,m.getDelta());assertEquals(TipoMovimientoStock.AJUSTE_MANUAL,m.getTipo());
        assertEquals(1L,m.getActorCuentaId());assertEquals("Conteo",m.getMotivo());assertNull(m.getReservaId());
        verify(audit).record(eq(1L),eq("ADMINISTRADOR"),eq("AJUSTAR_STOCK"),eq("STOCK"),eq(8L),
            isNull(),eq("Conteo"),argThat(b->b.get("cantidadDisponible").equals(10)),
            argThat(a->a.get("cantidadDisponible").equals(5)),isNull());
    }
    @Test void absentStockCreatesZeroBaselineBeforeAbsoluteAdjustment() {
        when(farmacias.findLocked(4)).thenReturn(Optional.of(farmacia()));
        when(stocks.saveAndFlush(any())).thenAnswer(call->{
            Stock s=call.getArgument(0);assertEquals(0,s.getCantidadDisponible());ReflectionTestUtils.setField(s,"id",8L);return s;
        });
        assertEquals(5,service.ajustar(4,3,new AjusteStockRequest(5,"Inicial"),admin,null).cantidadDisponible());
        verify(movimientos).saveAndFlush(argThat(m->m.getCantidadAnterior()==0 && m.getDelta()==5));
    }
    @Test void expirationsMustBeAppliedBeforeTakingPreviousAmount() {
        var s=stock(0);ready(s);
        doAnswer(call->{s.establecerCantidad(1,t);return null;}).when(integration)
            .materializarVencimientos(eq(4L),eq(3L),eq(t),same(s),anyList(),isNull());
        service.ajustar(4,3,new AjusteStockRequest(5,"Conteo"),admin,null);
        verify(movimientos).saveAndFlush(argThat(m->m.getCantidadAnterior()==1 && m.getCantidadNueva()==5 && m.getDelta()==4));
        var order=inOrder(integration,farmacias,stocks,movimientos);
        order.verify(integration).bloquearUsuariosVencidos(4,3,t);
        order.verify(farmacias).findLocked(4);
        order.verify(integration).bloquearCatalogoActivo(3);
        order.verify(stocks).findLocked(4,3);
        order.verify(integration).materializarVencimientos(eq(4L),eq(3L),eq(t),same(s),anyList(),isNull());
        order.verify(movimientos).saveAndFlush(any());
    }
    @Test void cannotPretendSuccessIfExpirationIntegrationFails() {
        var s=stock(0);ready(s);
        doThrow(ApiException.conflict("INVALID_STATE_TRANSITION","Compromiso inconsistente")).when(integration)
            .materializarVencimientos(eq(4L),eq(3L),eq(t),same(s),anyList(),isNull());
        assertThrows(ApiException.class,()->service.ajustar(4,3,new AjusteStockRequest(5,"Conteo"),admin,null));
        verifyNoInteractions(movimientos,audit);assertEquals(0,s.getCantidadDisponible());
    }
    @Test void negativeNullAndMissingReasonAreRejectedBeforeLocks() {
        for (var req:List.of(new AjusteStockRequest(-1,"Conteo"),new AjusteStockRequest(null,"Conteo"),
            new AjusteStockRequest(3," "),new AjusteStockRequest(3,"x".repeat(501)))) {
            assertEquals("VALIDATION_ERROR",assertThrows(ApiException.class,()->service.ajustar(4,3,req,admin,null)).codigo());
        }
        verifyNoInteractions(farmacias,stocks,movimientos,audit);
    }
    @Test void inactivePharmacyRejectsAdminAndOperator() {
        var f=farmacia();f.cambiarEstado(EstadoFarmacia.INACTIVA);
        when(farmacias.findLocked(4)).thenReturn(Optional.of(f));
        for (var actor:List.of(admin,operator)) assertEquals("PHARMACY_INACTIVE",assertThrows(ApiException.class,
            ()->service.ajustar(4,3,new AjusteStockRequest(5,"Conteo"),actor,null)).codigo());
        verifyNoInteractions(stocks,movimientos,audit);
    }
    @Test void operatorNeedsOperationalPharmacy() {
        when(farmacias.findLocked(4)).thenReturn(Optional.of(farmacia()));
        assertEquals("PHARMACY_NOT_OPERATIONAL",assertThrows(ApiException.class,
            ()->service.ajustar(4,3,new AjusteStockRequest(5,"Conteo"),operator,null)).codigo());
    }
    @Test void adminCanAdjustActiveNonOperationalPharmacy() {
        ready(stock(2));
        assertEquals(0,service.ajustar(4,3,new AjusteStockRequest(0,"Sin existencias"),admin,null).cantidadDisponible());
        verifyNoInteractions(pharmacyIntegration);
    }
    @Test void outOfScopeOperatorIsRejectedBeforeResourcesAreRead() {
        doThrow(ApiException.forbidden()).when(integration).autorizar(4,operator);
        assertThrows(ApiException.class,()->service.ajustar(4,3,new AjusteStockRequest(5,"Conteo"),operator,null));
        assertThrows(ApiException.class,()->service.listar(4,0,20,null,operator));
        verifyNoInteractions(farmacias,stocks,movimientos,audit);
    }
    @Test void inactiveCatalogRejectsAdjustment() {
        when(farmacias.findLocked(4)).thenReturn(Optional.of(farmacia()));
        doThrow(ApiException.conflict("MEDICATION_INACTIVE","Inactivo")).when(integration).bloquearCatalogoActivo(3);
        assertEquals("MEDICATION_INACTIVE",assertThrows(ApiException.class,
            ()->service.ajustar(4,3,new AjusteStockRequest(5,"Conteo"),admin,null)).codigo());
        verifyNoInteractions(stocks,movimientos,audit);
    }
    @Test void invalidMovementDateRangeIsRejected() {
        when(farmacias.existsById(4L)).thenReturn(true);
        var date=OffsetDateTime.ofInstant(t,ZoneOffset.UTC);
        assertThrows(ApiException.class,()->service.movimientos(4,null,date,date,0,20,null,admin));
        verifyNoInteractions(movimientos);
    }
}
