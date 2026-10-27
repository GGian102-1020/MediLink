package ar.edu.uade.medlink.reserva.controller;

import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.reserva.dto.ReservaDTO;
import ar.edu.uade.medlink.reserva.service.ReservaService;
import ar.edu.uade.medlink.security.CurrentAccount;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/usuarios/{id}/reservas")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class UsuarioReservasAdminController {
    private final ReservaService service;
    public UsuarioReservasAdminController(ReservaService service) { this.service=service; }
    @GetMapping
    public PageDTO<ReservaDTO> listar(@PathVariable long id,@RequestParam(required=false) String estado,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam MultiValueMap<String,String> all,@AuthenticationPrincipal CurrentAccount actor) {
        return service.historialAdmin(id,estado,desde,hasta,page,size,all.get("sort"),actor);
    }
}
