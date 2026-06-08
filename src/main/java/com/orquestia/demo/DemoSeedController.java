package com.orquestia.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint de utilidad para (re)generar la empresa de demostración del módulo
 * de predicción con Deep Learning. Requiere estar autenticado; la empresa demo
 * queda vinculada al admin que lo invoca para que pueda seleccionarla en la UI.
 *
 *   POST /api/admin/seed-demo  → crea/recrea "Demo Deep Learning"
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class DemoSeedController {

    private final DemoSeedService demoSeedService;

    @PostMapping("/seed-demo")
    public ResponseEntity<Map<String, Object>> seedDemo(Authentication auth) {
        return ResponseEntity.ok(demoSeedService.sembrar(auth.getName()));
    }
}
