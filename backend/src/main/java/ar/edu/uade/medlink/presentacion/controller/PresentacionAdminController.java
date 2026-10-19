package ar.edu.uade.medlink.presentacion.controller;

import ar.edu.uade.medlink.admin.AdminDtos.CambioEstadoRequest;
import ar.edu.uade.medlink.admin.AdminDtos.PageDTO;
import ar.edu.uade.medlink.presentacion.dto.*;
import ar.edu.uade.medlink.presentacion.service.PresentacionService;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/presentaciones")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class PresentacionAdminController {
    private final PresentacionService service;
    public PresentacionAdminController(PresentacionService service) { this.service=service; }
    @GetMapping
    public PageDTO<PresentacionDTO> listar(@RequestParam(required=false) Long medicamentoId,
        @RequestParam(required=false) String estado,@RequestParam(required=false) String condicionDispensa,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam MultiValueMap<String,String> params,@AuthenticationPrincipal CurrentAccount actor) {
        return service.listarAdmin(medicamentoId,estado,condicionDispensa,page,size,params.get("sort"),actor);
    }
    @GetMapping("/{id}")
    public PresentacionDTO detalle(@PathVariable long id,@AuthenticationPrincipal CurrentAccount actor) {
        return service.detalleAdmin(id,actor);
    }
    @PostMapping
    public ResponseEntity<PresentacionDTO> crear(@Valid @RequestBody PresentacionCreateRequest req,
            @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        return ResponseEntity.status(201).body(service.crear(req,actor,request));
    }
    @PostMapping("/{id}/desactivacion")
    public PresentacionDTO desactivar(@PathVariable long id,@Valid @RequestBody CambioEstadoRequest req,
            @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        return service.cambiarEstado(id,false,req.motivo(),actor,request);
    }
    @PostMapping("/{id}/reactivacion")
    public PresentacionDTO reactivar(@PathVariable long id,@Valid @RequestBody CambioEstadoRequest req,
            @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        return service.cambiarEstado(id,true,req.motivo(),actor,request);
    }
}
