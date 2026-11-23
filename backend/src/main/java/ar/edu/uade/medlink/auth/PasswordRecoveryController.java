package ar.edu.uade.medlink.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class PasswordRecoveryController {
    public record RecoveryRequest(@NotBlank String correo) { }
    public record ResetRequest(@NotBlank String correo, @NotBlank String codigo, @NotBlank String nuevaContrasena) { }
    public record Message(String mensaje) { }
    private final PasswordRecoveryService service;
    public PasswordRecoveryController(PasswordRecoveryService service) { this.service=service; }
    @PostMapping("/password-recovery")
    public ResponseEntity<Message> request(@RequestBody @Valid RecoveryRequest body, HttpServletRequest request) {
        service.request(body.correo(),request);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new Message(
            "Si el correo corresponde a una cuenta activa, recibirás un código. Revisá también el correo no deseado."));
    }
    @PostMapping("/password-reset")
    public ResponseEntity<Void> reset(@RequestBody @Valid ResetRequest body, HttpServletRequest request) {
        service.reset(body.correo(),body.codigo(),body.nuevaContrasena(),request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
