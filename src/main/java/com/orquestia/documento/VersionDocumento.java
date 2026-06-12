package com.orquestia.documento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Una versión histórica de un documento. Cada vez que OnlyOffice guarda
 * (cierre de sesión o Ctrl+S) se crea una versión nueva con su propia copia
 * en el almacenamiento de objetos (S3/MinIO), recuperable y descargable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionDocumento {
    private int version;          // 1, 2, 3...
    private String key;           // clave del objeto en S3/MinIO para esta versión
    private Instant fecha;        // cuándo se guardó esta versión
    private String editorId;      // userId de quien la guardó (si se conoce)
    private String editorNombre;  // nombre legible del editor
}
