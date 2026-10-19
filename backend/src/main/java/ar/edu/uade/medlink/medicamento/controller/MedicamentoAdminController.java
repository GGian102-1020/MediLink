package ar.edu.uade.medlink.medicamento.controller;

import ar.edu.uade.medlink.admin.AdminDtos.CambioEstadoRequest;
import ar.edu.uade.medlink.admin.AdminDtos.PageDTO;
import ar.edu.uade.medlink.medicamento.dto.*;
import ar.edu.uade.medlink.medicamento.service.MedicamentoService;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/medicamentos")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class MedicamentoAdminController {
    private final MedicamentoService service;
    public MedicamentoAdminController(MedicamentoService service) { this.service=service; }
    @GetMapping
    public PageDTO<MedicamentoDTO> listar(@RequestParam(required=false) String nombre,
            @RequestParam(required=false) String estado,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,@RequestParam MultiValueMap<String,String> params,
            @AuthenticationPrincipal CurrentAccount actor) {
        return service.listarAdmin(nombre,estado,page,size,params.get("sort"),actor);
    }
    @PostMapping
    public ResponseEntity<MedicamentoDTO> crear(@Valid @RequestBody MedicamentoRequest req,
            @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        return ResponseEntity.status(201).body(service.crear(req,actor,request));
    }
    @PutMapping("/{id}")
    public MedicamentoDTO editar(@PathVariable long id,@Valid @RequestBody MedicamentoRequest req,
            @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        return service.editar(id,req,actor,request);
    }
    @PostMapping("/{id}/desactivacion")
    public MedicamentoDTO desactivar(@PathVariable long id,@Valid @RequestBody CambioEstadoRequest req,
            @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        return service.cambiarEstado(id,false,req.motivo(),actor,request);
    }
    @PostMapping("/{id}/reactivacion")
    public MedicamentoDTO reactivar(@PathVariable long id,@Valid @RequestBody CambioEstadoRequest req,
            @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        return service.cambiarEstado(id,true,req.motivo(),actor,request);
    }
}
