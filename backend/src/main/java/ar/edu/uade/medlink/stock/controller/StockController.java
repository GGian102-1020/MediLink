package ar.edu.uade.medlink.stock.controller;

import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.security.CurrentAccount;
import ar.edu.uade.medlink.stock.dto.*;
import ar.edu.uade.medlink.stock.service.StockService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/farmacias/{id}/stock")
@PreAuthorize("hasAnyRole('OPERADOR','ADMINISTRADOR')")
public class StockController {
    private final StockService service;
    public StockController(StockService service) { this.service=service; }
    @GetMapping
    public PageDTO<StockDTO> listar(@PathVariable long id, @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size, @RequestParam MultiValueMap<String,String> params,
        @AuthenticationPrincipal CurrentAccount actor) {
        return service.listar(id,page,size,params.get("sort"),actor);
    }
    @GetMapping("/movimientos")
    public PageDTO<MovimientoStockDTO> movimientos(@PathVariable long id,
        @RequestParam(required=false) Long presentacionId,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
        @RequestParam MultiValueMap<String,String> params, @AuthenticationPrincipal CurrentAccount actor) {
        return service.movimientos(id,presentacionId,desde,hasta,page,size,params.get("sort"),actor);
    }
    @PutMapping("/{presentacionId}")
    public StockDTO ajustar(@PathVariable long id, @PathVariable long presentacionId,
        @Valid @RequestBody AjusteStockRequest req, @AuthenticationPrincipal CurrentAccount actor,
        HttpServletRequest request) {
        return service.ajustar(id,presentacionId,req,actor,request);
    }
}
