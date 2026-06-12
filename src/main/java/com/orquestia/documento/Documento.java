package com.orquestia.documento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "documentos")
public class Documento {

    @Id
    private String id;

    private String nombre;
    private String mimeType;
    private long size;
    private String key;        // clave en MinIO
    private String url;        // URL pública directa

    private String empresaId;
    private String instanciaId; // null si es un doc suelto de empresa
    private String tareaId;     // null si no está vinculado a una tarea

    private String procesoId;   // ID del proceso al que pertenece (null si es corporativo)
    private String clienteId;   // userId del cliente dueño del doc (null si es interno)
    private String tareaLabel;  // nombre de la actividad que originó el documento (si vino de una tarea)
    private String departamentoId; // depto de la actividad que originó el doc — define quién puede editar por defecto

    // ENTRADA     = requisito inicial del proceso
    // TAREA       = subido durante una actividad
    // GENERADO    = generado por el sistema / IA
    // CORPORATIVO = doc de empresa sin proceso asociado (plantillas, políticas)
    private TipoDocumento tipo;

    private String creadoPor;
    private String creadoPorNombre;
    private Instant fechaCreacion;

    // Clave única para OnlyOffice — cambia cada vez que se guarda una nueva versión
    private String editorKey;

    // Solo para docs de OnlyOffice — última versión guardada
    private Instant ultimaEdicion;
    private String ultimoEditorId;
    private String ultimoEditorNombre;

    // Versionamiento: número de la versión actual + historial de versiones recuperables
    @Builder.Default
    private int version = 1;

    @Builder.Default
    private List<VersionDocumento> versiones = new ArrayList<>();

    @Builder.Default
    private List<PermisoDocumento> permisos = new ArrayList<>();

    @Builder.Default
    private List<AuditEntry> auditLog = new ArrayList<>();

    public enum TipoDocumento { ENTRADA, TAREA, GENERADO, CORPORATIVO }
}
