package ar.edu.uade.medlink.reserva.controller;

import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.reserva.dto.*;
import ar.edu.uade.medlink.reserva.service.ReservaService;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservas")
@PreAuthorize("hasRole('USUARIO')")
public class ReservaController {
    private final ReservaService service;
    public ReservaController(ReservaService service) { this.service=service; }

    @PostMapping
    public ResponseEntity<ReservaDTO> crear(@RequestHeader(value="Idempotency-Key",required=false) String rawKey,
        @Valid @RequestBody CrearReservaRequest body,@AuthenticationPrincipal CurrentAccount actor,
        HttpServletRequest request) {
        UUID key;
        try {
            if (rawKey==null || !rawKey.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
                throw new IllegalArgumentException();
            key=UUID.fromString(rawKey);
        }
        catch(IllegalArgumentException ex) { throw ApiException.validation("Idempotency-Key debe ser UUID."); }
        var result=service.crear(body,key,actor,request);
        if (result.suspended()) throw ApiException.conflict("USER_SUSPENDED","Usuario suspendido.");
        return ResponseEntity.status(201).body(result.dto());
    }
    @GetMapping("/mias")
    public PageDTO<ReservaDTO> propias(@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size,@RequestParam(required=false) String estado,
        @RequestParam MultiValueMap<String,String> all,@AuthenticationPrincipal CurrentAccount actor) {
        return service.propias(page,size,estado,all.get("sort"),actor);
    }
    @GetMapping("/mias/{codigo}")
    public ReservaDTO propia(@PathVariable String codigo,@AuthenticationPrincipal CurrentAccount actor) {
        return service.propia(codigo,actor);
    }
    @PostMapping("/{codigo}/cancelacion")
    public ReservaDTO cancelar(@PathVariable String codigo,@AuthenticationPrincipal CurrentAccount actor,
        HttpServletRequest request) {
        var outcome=service.cancelarPropia(codigo,actor,request);
        if (outcome.expired()) throw ApiException.conflict("RESERVATION_EXPIRED","La reserva venció.");
        return outcome.dto();
    }
}
