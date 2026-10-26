package ar.edu.uade.medlink.prescripcion.controller;

import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.prescripcion.dto.PrescripcionDTO;
import ar.edu.uade.medlink.prescripcion.service.PrescripcionService;
import ar.edu.uade.medlink.security.CurrentAccount;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

/** A patient's own prescriptions, to choose one when reserving a BAJO_RECETA presentation. */
@RestController
@RequestMapping("/api/me/prescripciones")
@PreAuthorize("hasRole('USUARIO')")
public class PrescripcionController {
    private final PrescripcionService service;
    public PrescripcionController(PrescripcionService service) { this.service=service; }
    @GetMapping
    public PageDTO<PrescripcionDTO> listar(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,@RequestParam MultiValueMap<String,String> params,
            @AuthenticationPrincipal CurrentAccount actor) {
        return service.listarPropias(page,size,params.get("sort"),actor);
    }
    @GetMapping("/{id}")
    public PrescripcionDTO detalle(@PathVariable long id,@AuthenticationPrincipal CurrentAccount actor) {
        return service.detallePropio(id,actor);
    }
}
