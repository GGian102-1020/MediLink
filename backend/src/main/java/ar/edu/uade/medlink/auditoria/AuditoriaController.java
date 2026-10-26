package ar.edu.uade.medlink.auditoria;

import ar.edu.uade.medlink.common.AuditService;
import ar.edu.uade.medlink.common.PageDTO;
import ar.edu.uade.medlink.common.error.ApiException;
import ar.edu.uade.medlink.security.CurrentAccount;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auditoria")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AuditoriaController {
    private final AuditService audit;
    public AuditoriaController(AuditService audit) { this.audit=audit; }

    @GetMapping
    public PageDTO<AuditoriaDTO> listar(@RequestParam(required=false) Long usuarioId,
        @RequestParam(required=false) Long actorCuentaId, @RequestParam(required=false) String rol,
        @RequestParam(required=false) String accion, @RequestParam(required=false) String tipoEntidad,
        @RequestParam(required=false) Long entidadId,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
        @RequestParam MultiValueMap<String,String> params, @AuthenticationPrincipal CurrentAccount actor) {
        // Empty and repeated scalar filters must not silently broaden a query.
        params.forEach((key,values)-> {
            if (java.util.Set.of("usuarioId","actorCuentaId","rol","accion","tipoEntidad","entidadId",
                "desde","hasta","page","size","sort").contains(key)
                && (values.stream().anyMatch(String::isBlank) || (!key.equals("sort") && values.size()!=1)))
                throw ApiException.validation("Parámetro de auditoría vacío o repetido.");
        });
        return audit.listar(new AuditoriaFiltro(usuarioId,actorCuentaId,rol,accion,tipoEntidad,entidadId,
            desde,hasta),page,size,params.get("sort"),actor);
    }
}
