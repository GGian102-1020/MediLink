package ar.edu.uade.medlink.farmacia.controller;

import ar.edu.uade.medlink.admin.AdminDtos.*;
import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.farmacia.dto.*;
import ar.edu.uade.medlink.farmacia.service.FarmaciaService;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/farmacias")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class FarmaciaController {
    private final FarmaciaService service;
    public FarmaciaController(FarmaciaService service) { this.service=service; }
    @GetMapping
    public PageDTO<FarmaciaDTO> listar(@RequestParam(required=false) String nombre,
        @RequestParam(required=false) String estado, @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size, @RequestParam MultiValueMap<String,String> params,
        @AuthenticationPrincipal CurrentAccount actor) {
        return service.listar(nombre,estado,page,size,params.get("sort"),actor);
    }
    @PostMapping
    public ResponseEntity<FarmaciaDTO> crear(@Valid @RequestBody FarmaciaRequest req,
        @AuthenticationPrincipal CurrentAccount actor, HttpServletRequest request) {
        return ResponseEntity.status(201).body(service.crear(req,actor,request));
    }
    @PutMapping("/{id}")
    public FarmaciaDTO editar(@PathVariable long id, @Valid @RequestBody FarmaciaRequest req,
        @AuthenticationPrincipal CurrentAccount actor, HttpServletRequest request) {
        return service.editar(id,req,actor,request);
    }
    @PostMapping("/{id}/desactivacion")
    public FarmaciaDTO desactivar(@PathVariable long id, @Valid @RequestBody CambioEstadoRequest req,
        @AuthenticationPrincipal CurrentAccount actor, HttpServletRequest request) {
        return service.cambiarEstado(id,false,req.motivo(),actor,request);
    }
    @PostMapping("/{id}/reactivacion")
    public FarmaciaDTO reactivar(@PathVariable long id, @Valid @RequestBody CambioEstadoRequest req,
        @AuthenticationPrincipal CurrentAccount actor, HttpServletRequest request) {
        return service.cambiarEstado(id,true,req.motivo(),actor,request);
    }
}
