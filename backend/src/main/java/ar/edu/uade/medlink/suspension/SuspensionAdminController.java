package ar.edu.uade.medlink.suspension;

import ar.edu.uade.medlink.auth.AuthDtos.SuspensionDTO;
import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

/** Read-only RN-47 / AC-53 administrative history; there are no manual suspension commands. */
@RestController
@RequestMapping("/api/admin/usuarios/{id}/suspensiones")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class SuspensionAdminController {
    private final SuspensionConsultaService service;
    private static final Set<String> PARAMETERS = Set.of(
        "vigente", "desde", "hasta", "page", "size", "sort");

    public SuspensionAdminController(SuspensionConsultaService service) { this.service = service; }

    @GetMapping
    public PageDTO<SuspensionDTO> listar(@PathVariable long id,
        @RequestParam(required = false) Boolean vigente,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam MultiValueMap<String, String> params,
        @AuthenticationPrincipal CurrentAccount actor) {
        params.forEach((key, values) -> {
            if (PARAMETERS.contains(key) &&
                (values.stream().anyMatch(String::isBlank) ||
                    (!"sort".equals(key) && values.size() != 1)))
                throw ApiException.validation("Parámetro de suspensiones vacío o repetido.");
        });
        return service.listar(id, vigente, desde, hasta, page, size, params.get("sort"), actor);
    }
}
