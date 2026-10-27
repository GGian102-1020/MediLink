package ar.edu.uade.medlink.suspension.controller;

import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import ar.edu.uade.medlink.suspension.dto.SuspensionResponseDTO;
import ar.edu.uade.medlink.suspension.service.SuspensionService;
import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

/** Section 11.8: only the administrative, read-only history endpoint is public to an admin. */
@RestController
@RequestMapping("/api/admin/usuarios/{usuarioId}/suspensiones")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class SuspensionController {
    private final SuspensionService suspensionService;

    public SuspensionController(SuspensionService suspensionService) {
        this.suspensionService=suspensionService;
    }

    @GetMapping
    public PageDTO<SuspensionResponseDTO> listar(@PathVariable long usuarioId,
            @RequestParam(required=false) Boolean vigente,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,
            @RequestParam MultiValueMap<String,String> params,
            @AuthenticationPrincipal CurrentAccount actor) {
        Set<String> known=Set.of("vigente","desde","hasta","page","size","sort");
        params.forEach((key,values)-> {
            if (known.contains(key) && (values.stream().anyMatch(String::isBlank)
                || (!key.equals("sort") && values.size()!=1)))
                throw ApiException.validation("Parámetro de suspensión vacío o repetido.");
        });
        return suspensionService.listar(usuarioId,vigente,desde,hasta,page,size,params.get("sort"),actor);
    }
}
