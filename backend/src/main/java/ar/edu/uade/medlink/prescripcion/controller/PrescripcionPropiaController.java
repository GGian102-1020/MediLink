package ar.edu.uade.medlink.prescripcion.controller;

import ar.edu.uade.medlink.auth.AccountRepository;
import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.prescripcion.dto.PrescripcionDTO;
import ar.edu.uade.medlink.prescripcion.service.PrescripcionService;
import ar.edu.uade.medlink.security.CurrentAccount;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/prescripciones")
@PreAuthorize("hasRole('USUARIO')")
public class PrescripcionPropiaController {
    private final PrescripcionService prescripciones;
    private final AccountRepository cuentas;

    public PrescripcionPropiaController(PrescripcionService prescripciones, AccountRepository cuentas) {
        this.prescripciones=prescripciones;
        this.cuentas=cuentas;
    }

    @GetMapping
    public ResponseEntity<PageDTO<PrescripcionDTO>> listar(@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size, @AuthenticationPrincipal CurrentAccount actor) {
        long usuario=cuentas.userProfile(actor.id()).usuarioId();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(prescripciones.listarPropias(usuario,page,size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrescripcionDTO> detalle(@PathVariable long id, @AuthenticationPrincipal CurrentAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(prescripciones.detallePropio(id,cuentas.userProfile(actor.id()).usuarioId()));
    }
}
