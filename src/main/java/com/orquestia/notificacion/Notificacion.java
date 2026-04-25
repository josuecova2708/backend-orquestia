package com.orquestia.notificacion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "notificaciones")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Notificacion {
    @Id
    private String id;
    private String userId;
    private String tipo; // TAREA_ASIGNADA | DEPT_INVITACION
    private String mensaje;
    @Builder.Default
    private boolean leida = false;
    @Builder.Default
    private LocalDateTime fecha = LocalDateTime.now();
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
