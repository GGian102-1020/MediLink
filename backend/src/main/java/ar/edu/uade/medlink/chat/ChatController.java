package ar.edu.uade.medlink.chat;

import ar.edu.uade.medlink.chat.ChatDtos.*;
import ar.edu.uade.medlink.security.CurrentAccount;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Chat desde el dashboard: el operador con los pacientes que reservaron en su farmacia. */
@RestController
@RequestMapping("/api/farmacias/{farmaciaId}/chats")
@PreAuthorize("hasRole('OPERADOR')")
class FarmaciaChatController {
    private final ChatService service;
    FarmaciaChatController(ChatService service) { this.service = service; }

    @GetMapping
    List<ConversacionFarmaciaDTO> conversaciones(@PathVariable long farmaciaId,
            @RequestParam(required = false) String q, @AuthenticationPrincipal CurrentAccount actor) {
        return service.conversacionesFarmacia(farmaciaId, q, actor);
    }

    @GetMapping("/no-leidos")
    NoLeidosDTO noLeidos(@PathVariable long farmaciaId, @AuthenticationPrincipal CurrentAccount actor) {
        return service.noLeidosFarmacia(farmaciaId, actor);
    }

    /** Si se puede escribir (reserva abierta) y a qué reserva corresponde la conversación. */
    @GetMapping("/{usuarioId}")
    EstadoConversacionDTO estado(@PathVariable long farmaciaId, @PathVariable long usuarioId,
            @AuthenticationPrincipal CurrentAccount actor) {
        return service.estadoFarmacia(farmaciaId, usuarioId, actor);
    }

    @GetMapping("/{usuarioId}/mensajes")
    List<MensajeDTO> mensajes(@PathVariable long farmaciaId, @PathVariable long usuarioId,
            @RequestParam(required = false) Long despuesDe, @AuthenticationPrincipal CurrentAccount actor) {
        return service.mensajesFarmacia(farmaciaId, usuarioId, despuesDe, actor);
    }

    @PostMapping("/{usuarioId}/mensajes")
    ResponseEntity<MensajeDTO> enviar(@PathVariable long farmaciaId, @PathVariable long usuarioId,
            @RequestBody(required = false) EnviarMensajeRequest body, @AuthenticationPrincipal CurrentAccount actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.enviarFarmacia(farmaciaId, usuarioId, body, actor));
    }
}

/** Chat desde la app: el paciente con las farmacias donde reservó. */
@RestController
@RequestMapping("/api/chats")
@PreAuthorize("hasRole('USUARIO')")
class UsuarioChatController {
    private final ChatService service;
    UsuarioChatController(ChatService service) { this.service = service; }

    @GetMapping
    List<ConversacionUsuarioDTO> conversaciones(@AuthenticationPrincipal CurrentAccount actor) {
        return service.conversacionesUsuario(actor);
    }

    @GetMapping("/no-leidos")
    NoLeidosDTO noLeidos(@AuthenticationPrincipal CurrentAccount actor) {
        return service.noLeidosUsuario(actor);
    }

    /** Si se puede escribir (reserva abierta) y a qué reserva corresponde la conversación. */
    @GetMapping("/{farmaciaId}")
    EstadoConversacionDTO estado(@PathVariable long farmaciaId, @AuthenticationPrincipal CurrentAccount actor) {
        return service.estadoUsuario(farmaciaId, actor);
    }

    @GetMapping("/{farmaciaId}/mensajes")
    List<MensajeDTO> mensajes(@PathVariable long farmaciaId, @RequestParam(required = false) Long despuesDe,
            @AuthenticationPrincipal CurrentAccount actor) {
        return service.mensajesUsuario(farmaciaId, despuesDe, actor);
    }

    @PostMapping("/{farmaciaId}/mensajes")
    ResponseEntity<MensajeDTO> enviar(@PathVariable long farmaciaId,
            @RequestBody(required = false) EnviarMensajeRequest body, @AuthenticationPrincipal CurrentAccount actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.enviarUsuario(farmaciaId, body, actor));
    }
}
