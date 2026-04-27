package com.orquestia.notificacion;

import com.orquestia.auth.Usuario;
import com.orquestia.auth.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Endpoint de diagnóstico para verificar que FCM está funcionando.
 * Envía una push de prueba al usuario autenticado.
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestPushController {

    private final UsuarioRepository usuarioRepository;
    private final FcmService fcmService;

    @PostMapping("/push")
    public ResponseEntity<Map<String, Object>> testPush(Authentication auth) {
        if (auth == null) {
            log.warn("testPush: auth is null — JWT inválido o ausente");
            return ResponseEntity.ok(Map.of(
                    "status", "NO_AUTH",
                    "mensaje", "No se pudo autenticar. Cierra sesión y vuelve a entrar."
            ));
        }

        log.info("testPush: userId={}", auth.getName());

        Usuario usuario = usuarioRepository.findById(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + auth.getName()));

        List<String> tokens = usuario.getDeviceTokens();
        log.info("testPush: userId={} tokens={}", auth.getName(), tokens == null ? "null" : tokens.size());

        if (tokens == null || tokens.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "status", "NO_TOKENS",
                    "mensaje", "El dispositivo no tiene tokens FCM. Cierra sesión, vuelve a entrar y reintenta.",
                    "userId", usuario.getId()
            ));
        }

        fcmService.sendPush(
                tokens,
                "🔔 Test Orquestia",
                "¡Firebase está conectado correctamente!",
                Map.of("tipo", "TEST")
        );

        return ResponseEntity.ok(Map.of(
                "status", "SENT",
                "mensaje", "Push enviada a " + tokens.size() + " dispositivo(s)",
                "tokens", tokens.size()
        ));
    }
}
