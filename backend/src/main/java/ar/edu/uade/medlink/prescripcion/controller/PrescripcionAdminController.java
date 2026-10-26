package ar.edu.uade.medlink.prescripcion.controller;

import ar.edu.uade.medlink.admin.AdminDtos.CambioEstadoRequest;
import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.prescripcion.dto.*;
import ar.edu.uade.medlink.prescripcion.service.PrescripcionService;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/prescripciones")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class PrescripcionAdminController {
    private final PrescripcionService service;
    public PrescripcionAdminController(PrescripcionService service) { this.service=service; }
    @GetMapping
    public PageDTO<PrescripcionDTO> listar(@RequestParam(required=false) Long usuarioId,
            @RequestParam(required=false) String estado,@RequestParam(required=false) String codigoExterno,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam MultiValueMap<String,String> params,@AuthenticationPrincipal CurrentAccount actor) {
        return service.listarAdmin(usuarioId,estado,codigoExterno,page,size,params.get("sort"),actor);
    }
    @GetMapping("/{id}")
    public PrescripcionDTO detalle(@PathVariable long id,@AuthenticationPrincipal CurrentAccount actor) {
        return service.detalleAdmin(id,actor);
    }
    @PostMapping
    public ResponseEntity<PrescripcionDTO> crear(@Valid @RequestBody PrescripcionCreateRequest req,
            @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        return ResponseEntity.status(201).body(service.crear(req,actor,request));
    }
    @PostMapping("/{id}/anulacion")
    public PrescripcionDTO anular(@PathVariable long id,@Valid @RequestBody CambioEstadoRequest req,
            @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        return service.anular(id,req.motivo(),actor,request);
    }
}
