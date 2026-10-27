package ar.edu.uade.medlink.reserva.controller;

import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.reserva.dto.*;
import ar.edu.uade.medlink.reserva.service.ReservaService;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/farmacias/{id}/reservas")
@PreAuthorize("hasAnyRole('OPERADOR','ADMINISTRADOR')")
public class FarmaciaReservaController {
    private final ReservaService service;
    public FarmaciaReservaController(ReservaService service) { this.service=service; }
    @GetMapping
    public PageDTO<ReservaDTO> listar(@PathVariable long id,
        @RequestParam(required=false) String estado,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam MultiValueMap<String,String> all,@AuthenticationPrincipal CurrentAccount actor) {
        return service.deFarmacia(id,estado,desde,hasta,page,size,all.get("sort"),actor);
    }
    @GetMapping("/{codigo}")
    public ReservaDTO detalle(@PathVariable long id,@PathVariable String codigo,
        @AuthenticationPrincipal CurrentAccount actor) {
        return service.detalleFarmacia(id,codigo,actor);
    }
    @PostMapping("/{codigo}/retiro")
    @PreAuthorize("hasRole('OPERADOR')")
    public ReservaDTO retirar(@PathVariable long id,@PathVariable String codigo,
        @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        var result=service.retirar(id,codigo,actor,request);
        if (result.expired()) throw ApiException.conflict("RESERVATION_EXPIRED","La reserva venció.");
        return result.dto();
    }
    @PostMapping("/{codigo}/cancelacion-operativa")
    public ReservaDTO cancelar(@PathVariable long id,@PathVariable String codigo,
        @Valid @RequestBody CancelacionOperativaRequest cmd,
        @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        var result=service.cancelarOperativa(id,codigo,cmd,actor,request);
        if (result.expired()) throw ApiException.conflict("RESERVATION_EXPIRED","La reserva venció.");
        return result.dto();
    }
}
