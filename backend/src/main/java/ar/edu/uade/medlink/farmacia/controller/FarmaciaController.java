package ar.edu.uade.medlink.farmacia.controller;

import ar.edu.uade.medlink.admin.AdminDtos.*;
import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.farmacia.dto.*;
import ar.edu.uade.medlink.farmacia.service.FarmaciaService;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class FarmaciaController {
    private final FarmaciaService service;
    public FarmaciaController(FarmaciaService service) { this.service=service; }

    // ---- Endpoints de administración (solo ADMINISTRADOR) ---------------------

    @GetMapping("/api/admin/farmacias")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public PageDTO<FarmaciaDTO> listar(@RequestParam(required=false) String nombre,
        @RequestParam(required=false) String estado, @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size, @RequestParam MultiValueMap<String,String> params,
        @AuthenticationPrincipal CurrentAccount actor) {
        return service.listar(nombre,estado,page,size,params.get("sort"),actor);
    }

    @PostMapping("/api/admin/farmacias")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<FarmaciaDTO> crear(@Valid @RequestBody FarmaciaRequest req,
        @AuthenticationPrincipal CurrentAccount actor, HttpServletRequest request) {
        return ResponseEntity.status(201).body(service.crear(req,actor,request));
    }

    @PutMapping("/api/admin/farmacias/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public FarmaciaDTO editar(@PathVariable long id, @Valid @RequestBody FarmaciaRequest req,
        @AuthenticationPrincipal CurrentAccount actor, HttpServletRequest request) {
        return service.editar(id,req,actor,request);
    }

    @PostMapping("/api/admin/farmacias/{id}/desactivacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public FarmaciaDTO desactivar(@PathVariable long id, @Valid @RequestBody CambioEstadoRequest req,
        @AuthenticationPrincipal CurrentAccount actor, HttpServletRequest request) {
        return service.cambiarEstado(id,false,req.motivo(),actor,request);
    }

    @PostMapping("/api/admin/farmacias/{id}/reactivacion")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public FarmaciaDTO reactivar(@PathVariable long id, @Valid @RequestBody CambioEstadoRequest req,
        @AuthenticationPrincipal CurrentAccount actor, HttpServletRequest request) {
        return service.cambiarEstado(id,true,req.motivo(),actor,request);
    }

    // ---- Endpoint público (cualquier usuario autenticado) ---------------------

    /**
     * GET /api/farmacias/cercanas?lat=-34.603&lng=-58.381&radio=5
     *
     * Devuelve las farmacias ACTIVAS ordenadas por distancia (Haversine).
     * El radio es opcional; por defecto 10 km.
     */
    @GetMapping("/api/farmacias/cercanas")
    @PreAuthorize("isAuthenticated()")
    public List<FarmaciaCercanaDTO> cercanas(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0")  BigDecimal lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lng,
            @RequestParam(defaultValue="10.0") double radio) {
        return service.buscarCercanas(lat, lng, radio);
    }
}
