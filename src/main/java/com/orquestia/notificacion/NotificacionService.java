package com.orquestia.notificacion;

import com.orquestia.auth.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final FcmService fcmService;

    public Notificacion crear(String userId, String tipo, String mensaje, Map<String, Object> metadata) {
        Notificacion notif = notificacionRepository.save(
            Notificacion.builder()
                .userId(userId)
                .tipo(tipo)
                .mensaje(mensaje)
                .metadata(metadata != null ? metadata : Map.of())
                .build()
        );

        usuarioRepository.findById(userId).ifPresent(usuario -> {
            if (!usuario.getDeviceTokens().isEmpty()) {
                Map<String, String> data = Map.of(
                        "tipo", tipo,
                        "notificacionId", notif.getId()
                );
                fcmService.sendPush(usuario.getDeviceTokens(), "Orquestia", mensaje, data);
            }
        });

        return notif;
    }

    public List<Notificacion> listar(String userId) {
        return notificacionRepository.findByUserIdOrderByFechaDesc(userId);
    }

    public long contarNoLeidas(String userId) {
        return notificacionRepository.countByUserIdAndLeida(userId, false);
    }

    public Notificacion marcarLeida(String id) {
        Notificacion n = notificacionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notificación no encontrada: " + id));
        n.setLeida(true);
        return notificacionRepository.save(n);
    }

    public void marcarTodasLeidas(String userId) {
        notificacionRepository.findByUserIdOrderByFechaDesc(userId).stream()
                .filter(n -> !n.isLeida())
                .forEach(n -> { n.setLeida(true); notificacionRepository.save(n); });
    }
}
