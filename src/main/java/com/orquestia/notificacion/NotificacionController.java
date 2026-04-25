package com.orquestia.notificacion;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService notificacionService;

    @GetMapping
    public ResponseEntity<List<Notificacion>> listar(Authentication auth) {
        return ResponseEntity.ok(notificacionService.listar(auth.getName()));
    }

    @GetMapping("/no-leidas")
    public ResponseEntity<Long> contarNoLeidas(Authentication auth) {
        return ResponseEntity.ok(notificacionService.contarNoLeidas(auth.getName()));
    }

    @PutMapping("/{id}/leer")
    public ResponseEntity<Notificacion> marcarLeida(@PathVariable String id) {
        return ResponseEntity.ok(notificacionService.marcarLeida(id));
    }

    @PutMapping("/leer-todas")
    public ResponseEntity<Void> marcarTodasLeidas(Authentication auth) {
        notificacionService.marcarTodasLeidas(auth.getName());
        return ResponseEntity.noContent().build();
    }
}
