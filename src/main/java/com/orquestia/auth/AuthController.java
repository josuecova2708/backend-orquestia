package com.orquestia.auth;

import com.orquestia.auth.dto.AuthResponse;
import com.orquestia.auth.dto.InvitarAdminRequest;
import com.orquestia.auth.dto.LoginRequest;
import com.orquestia.auth.dto.RegisterRequest;
import com.orquestia.auth.dto.SetupEmpresaRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints:
 *   POST /api/auth/register
 *   POST /api/auth/login
 *   POST /api/auth/setup-empresa       → crear nueva empresa (multi-empresa permitido)
 *   POST /api/auth/switch-empresa/{id} → cambiar empresa activa (multi-admin)
 *   POST /api/auth/invitar-admin       → invitar co-admin a una empresa
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/setup-empresa")
    public ResponseEntity<AuthResponse> setupEmpresa(
            @Valid @RequestBody SetupEmpresaRequest request,
            Authentication auth) {
        return ResponseEntity.ok(authService.setupEmpresa(auth.getName(), request));
    }

    @PostMapping("/switch-empresa/{empresaId}")
    public ResponseEntity<AuthResponse> switchEmpresa(
            @PathVariable String empresaId,
            Authentication auth) {
        return ResponseEntity.ok(authService.switchEmpresa(auth.getName(), empresaId));
    }

    @PostMapping("/invitar-admin")
    public ResponseEntity<AuthResponse> invitarAdmin(
            @RequestBody InvitarAdminRequest request,
            Authentication auth) {
        return ResponseEntity.ok(authService.invitarAdmin(request.getEmpresaId(), request, auth.getName()));
    }
}
