package ar.edu.uade.medlink.auth;

import ar.edu.uade.medlink.auth.AuthDtos.*;
import ar.edu.uade.medlink.security.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }

    @PostMapping("/auth/register")
    public ResponseEntity<UsuarioPerfilDTO> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request));
    }
    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) { return service.login(request); }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal CurrentAccount current,
                       @RequestHeader("Authorization") String authorization) {
        service.logout(current, authorization.substring(7));
    }
    @PostMapping("/auth/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal CurrentAccount current,
        @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
        service.changePassword(current,request,httpRequest);
    }
    @GetMapping("/me")
    public MeDTO me(@AuthenticationPrincipal CurrentAccount current) { return service.me(current); }
}
