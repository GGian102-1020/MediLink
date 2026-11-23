package ar.edu.uade.medlink.recetaadjunta;

import ar.edu.uade.medlink.recetaadjunta.RecetaAdjuntaService.RecetaAdjuntaDTO;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** El paciente adjunta la receta (PDF o imagen) antes de confirmar una reserva BAJO_RECETA. */
@RestController
@RequestMapping("/api/me/recetas-adjuntas")
@PreAuthorize("hasRole('USUARIO')")
class RecetaAdjuntaUsuarioController {
    private final RecetaAdjuntaService service;
    RecetaAdjuntaUsuarioController(RecetaAdjuntaService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<RecetaAdjuntaDTO> adjuntar(@RequestPart(value = "archivo", required = false) MultipartFile archivo,
            @RequestParam(required = false) Long presentacionId, @RequestParam(required = false) Integer cantidad,
            @AuthenticationPrincipal CurrentAccount actor, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(service.adjuntar(archivo, presentacionId, cantidad, actor, request));
    }
}

/** La farmacia consulta la receta adjunta de una reserva para verificarla al entregar. */
@RestController
@RequestMapping("/api/farmacias/{farmaciaId}/reservas/{codigo}/receta")
@PreAuthorize("hasRole('OPERADOR')")
class RecetaAdjuntaFarmaciaController {
    private final RecetaAdjuntaService service;
    RecetaAdjuntaFarmaciaController(RecetaAdjuntaService service) { this.service = service; }

    @GetMapping
    ResponseEntity<byte[]> ver(@PathVariable long farmaciaId, @PathVariable String codigo,
            @AuthenticationPrincipal CurrentAccount actor) {
        var archivo = service.deReservaDeFarmacia(farmaciaId, codigo, actor);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(archivo.contentType()))
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                .filename(archivo.nombre(), StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(archivo.contenido());
    }
}
