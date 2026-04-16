package com.orquestia.auth;

import com.orquestia.auth.dto.AuthResponse;
import com.orquestia.auth.dto.LoginRequest;
import com.orquestia.auth.dto.RegisterRequest;
import com.orquestia.auth.dto.SetupEmpresaRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para autenticación.
 *
 * Endpoints:
 *   POST /api/auth/register         → Crear cuenta nueva
 *   POST /api/auth/login            → Iniciar sesión
 *   POST /api/auth/setup-empresa    → Onboarding: crear empresa y vincular al usuario (requiere JWT)
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

    /**
     * Onboarding: el usuario ya está logueado (tiene JWT) pero no tiene empresa.
     * Crea la empresa y lo vincula, retornando un nuevo token con empresaId incluido.
     * Authentication.getName() retorna el userId del JWT (lo pusimos como subject).
     */
    @PostMapping("/setup-empresa")
    public ResponseEntity<AuthResponse> setupEmpresa(
            @Valid @RequestBody SetupEmpresaRequest request,
            Authentication auth) {
        String userId = auth.getName();
        return ResponseEntity.ok(authService.setupEmpresa(userId, request));
    }
}

