package com.orquestia.auth;

import com.orquestia.auth.dto.AuthResponse;
import com.orquestia.auth.dto.LoginRequest;
import com.orquestia.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST para autenticación.
 *
 * @RestController → Le dice a Spring que esta clase maneja peticiones HTTP y retorna JSON.
 * @RequestMapping → Prefijo de URL para todos los endpoints de este controller.
 *
 * Endpoints:
 *   POST /api/auth/register → Crear cuenta nueva
 *   POST /api/auth/login    → Iniciar sesión
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
}
