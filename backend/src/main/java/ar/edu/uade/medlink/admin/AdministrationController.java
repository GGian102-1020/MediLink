package ar.edu.uade.medlink.admin;

import ar.edu.uade.medlink.admin.AdminDtos.*;
import ar.edu.uade.medlink.auth.AuthDtos.CuentaPerfilDTO;
import ar.edu.uade.medlink.auth.AuthDtos.UsuarioPerfilDTO;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdministrationController {
    private final AdministrationService service;
    public AdministrationController(AdministrationService service) { this.service=service; }

    @GetMapping("/operadores")
    public PageDTO<OperadorDTO> operators(@RequestParam(required=false) Long farmaciaId,
        @RequestParam(required=false) String estadoCuenta, @RequestParam(required=false) String correo,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam MultiValueMap<String,String> params) {
        return service.operators(farmaciaId,estadoCuenta,correo,page,size,params.get("sort"));
    }
    @GetMapping("/operadores/{id}")
    public OperadorDTO operator(@PathVariable long id) { return service.operator(id); }
    @PostMapping("/operadores")
    public ResponseEntity<OperadorDTO> create(@Valid @RequestBody OperadorCreateRequest req,
        @AuthenticationPrincipal CurrentAccount actor, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createOperator(req,actor,request));
    }
    @PutMapping("/operadores/{id}")
    public OperadorDTO update(@PathVariable long id,@Valid @RequestBody OperadorUpdateRequest req,
        @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        return service.updateOperator(id,req,actor,request);
    }
    @GetMapping("/usuarios")
    public PageDTO<UsuarioPerfilDTO> users(@RequestParam(required=false) String correo,
        @RequestParam(required=false) String estadoCuenta,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size,@RequestParam MultiValueMap<String,String> params) {
        return service.users(correo,estadoCuenta,page,size,params.get("sort"));
    }
    @PostMapping("/cuentas/{id}/deshabilitacion")
    public CuentaPerfilDTO disable(@PathVariable long id,@Valid @RequestBody CambioEstadoRequest req,
        @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        return service.changeState(id,false,req.motivo(),actor,request);
    }
    @PostMapping("/cuentas/{id}/reactivacion")
    public CuentaPerfilDTO enable(@PathVariable long id,@Valid @RequestBody CambioEstadoRequest req,
        @AuthenticationPrincipal CurrentAccount actor,HttpServletRequest request) {
        return service.changeState(id,true,req.motivo(),actor,request);
    }
}
