package com.orquestia.documento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEntry {
    private String usuarioId;
    private String usuarioNombre;
    private String accion; // CREAR, VER, EDITAR, DESCARGAR, ELIMINAR
    private Instant fecha;
    private String detalle;
}
