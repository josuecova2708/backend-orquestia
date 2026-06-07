package com.orquestia.documento;

import com.orquestia.auth.UsuarioRepository;
import com.orquestia.instancia.InstanciaRepository;
import com.orquestia.instancia.TareaRepository;
import com.orquestia.storage.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentoService {

    private final DocumentoRepository documentoRepository;
    private final MinioService minioService;
    private final OnlyOfficeJwtService onlyOfficeJwtService;
    private final UsuarioRepository usuarioRepository;
    private final InstanciaRepository instanciaRepository;
    private final TareaRepository tareaRepository;

    // --- Upload ---

    public record IniciarUploadRequest(
        String nombre,
        String mimeType,
        long size,
        String empresaId,
        String instanciaId,
        String tareaId,
        String procesoId,
        Documento.TipoDocumento tipo
    ) {}

    public record IniciarUploadResponse(
        String documentoId,
        String uploadUrl,
        String key,
        String publicUrl
    ) {}

    public IniciarUploadResponse iniciarUpload(IniciarUploadRequest req, String usuarioId) {
        String usuarioNombre = resolverNombre(usuarioId);

        // Auto-poblar procesoId y clienteId desde la instancia si está disponible
        String procesoId = req.procesoId();
        String clienteId = null;
        if (req.instanciaId() != null) {
            var instancia = instanciaRepository.findById(req.instanciaId()).orElse(null);
            if (instancia != null) {
                procesoId = instancia.getProcesoId();
                clienteId = instancia.getClienteId();
            }
        }

        // Sin instancia aún (docs ENTRADA que el cliente sube antes de iniciar el trámite):
        // si quien sube es un CLIENTE, el documento le pertenece.
        if (clienteId == null) {
            boolean esCliente = usuarioRepository.findById(usuarioId)
                .map(u -> u.getRol() != null && "CLIENTE".equals(u.getRol().name()))
                .orElse(false);
            if (esCliente) clienteId = usuarioId;
        }

        MinioService.PresignResult presign = minioService.generarPresignedUrlSGD(
            req.empresaId(), req.instanciaId(), req.nombre()
        );

        Documento doc = Documento.builder()
            .nombre(req.nombre())
            .mimeType(req.mimeType())
            .size(req.size())
            .key(presign.key())
            .url(presign.publicUrl())
            .empresaId(req.empresaId())
            .instanciaId(req.instanciaId())
            .tareaId(req.tareaId())
            .procesoId(procesoId)
            .clienteId(clienteId)
            .tipo(req.tipo() != null ? req.tipo() : Documento.TipoDocumento.TAREA)
            .creadoPor(usuarioId)
            .creadoPorNombre(usuarioNombre)
            .fechaCreacion(Instant.now())
            .build();

        doc.getAuditLog().add(AuditEntry.builder()
            .usuarioId(usuarioId)
            .usuarioNombre(usuarioNombre)
            .accion("CREAR")
            .fecha(Instant.now())
            .detalle("Documento registrado")
            .build());

        doc = documentoRepository.save(doc);
        return new IniciarUploadResponse(doc.getId(), presign.uploadUrl(), presign.key(), presign.publicUrl());
    }

    // --- Consulta ---

    public List<Documento> listarPorEmpresa(String empresaId) {
        return documentoRepository.findByEmpresaIdOrderByFechaCreacionDesc(empresaId);
    }

    public List<Documento> listarPorInstancia(String instanciaId) {
        return documentoRepository.findByInstanciaIdOrderByFechaCreacionDesc(instanciaId);
    }

    public List<Documento> listarPorCliente(String clienteId) {
        return documentoRepository.findByClienteIdOrderByFechaCreacionDesc(clienteId);
    }

    // Documentos visibles para un funcionario: solo de instancias donde tiene tareas asignadas
    public List<Documento> listarParaFuncionario(String usuarioId) {
        var usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Set<String> instanciaIds = new HashSet<>();

        // Tareas asignadas directamente al usuario
        tareaRepository.findByAsignadoA(usuarioId)
            .forEach(t -> instanciaIds.add(t.getInstanciaId()));

        // Tareas del departamento al que pertenece
        if (usuario.getDepartamentoId() != null) {
            tareaRepository.findByDepartamentoId(usuario.getDepartamentoId())
                .forEach(t -> instanciaIds.add(t.getInstanciaId()));
        }

        if (instanciaIds.isEmpty()) return List.of();
        return documentoRepository.findByInstanciaIdInOrderByFechaCreacionDesc(instanciaIds);
    }

    public Documento obtener(String id) {
        return documentoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Documento no encontrado"));
    }

    // Vincula documentos de entrada (subidos antes de existir la instancia) al trámite recién creado.
    // Setea instanciaId + procesoId y marca el tipo como ENTRADA.
    public List<Documento> vincularAInstancia(List<String> documentoIds, String instanciaId,
                                              String procesoId, String clienteId) {
        if (documentoIds == null || documentoIds.isEmpty()) return List.of();
        List<Documento> docs = documentoRepository.findAllById(documentoIds);
        for (Documento d : docs) {
            d.setInstanciaId(instanciaId);
            d.setProcesoId(procesoId);
            if (d.getClienteId() == null) d.setClienteId(clienteId);
            d.setTipo(Documento.TipoDocumento.ENTRADA);
        }
        return documentoRepository.saveAll(docs);
    }

    // --- Descarga (URL temporal) ---

    public String generarUrlDescarga(String id, String usuarioId) {
        Documento doc = obtener(id);
        String url = minioService.generarUrlDescarga(doc.getKey());

        doc.getAuditLog().add(AuditEntry.builder()
            .usuarioId(usuarioId)
            .usuarioNombre(resolverNombre(usuarioId))
            .accion("DESCARGAR")
            .fecha(Instant.now())
            .detalle("Descarga solicitada")
            .build());
        documentoRepository.save(doc);

        return url;
    }

    // --- OnlyOffice editor config ---

    public record OnlyOfficeConfig(
        String documentType,
        Map<String, Object> document,
        Map<String, Object> editorConfig,
        String token
    ) {}

    public OnlyOfficeConfig generarEditorConfig(
        String id,
        String backendBaseUrl,
        String usuarioId,
        String usuarioEmail,
        boolean soloLectura
    ) {
        Documento doc = obtener(id);
        String usuarioNombre = resolverNombre(usuarioId);

        // OnlyOffice descarga via proxy del backend — evita problemas de red con MinIO
        String downloadUrl = backendBaseUrl + "/api/documentos/" + id + "/stream";
        String callbackUrl = backendBaseUrl + "/api/documentos/" + id + "/callback";
        String extension = obtenerExtension(doc.getNombre());
        String documentType = resolverTipoDocumento(extension);

        // Si no tiene editorKey aún, generar uno y persistirlo
        if (doc.getEditorKey() == null) {
            doc.setEditorKey(UUID.randomUUID().toString().replace("-", "").substring(0, 20));
            documentoRepository.save(doc);
        }

        Map<String, Object> documentMap = Map.of(
            "fileType", extension,
            "key", doc.getEditorKey(),
            "title", doc.getNombre(),
            "url", downloadUrl,
            "permissions", Map.of(
                "edit", !soloLectura,
                "download", true,
                "print", true
            )
        );

        Map<String, Object> userMap = Map.of(
            "id", usuarioId,
            "name", usuarioNombre
        );

        // forcesave: true habilita el botón Guardar manual (Ctrl+S) en el editor
        Map<String, Object> customization = Map.of(
            "forcesave", true,
            "autosave", true
        );

        Map<String, Object> editorConfig = new HashMap<>();
        editorConfig.put("callbackUrl", callbackUrl);
        editorConfig.put("mode", soloLectura ? "view" : "edit");
        editorConfig.put("lang", "es");
        editorConfig.put("user", userMap);
        if (!soloLectura) editorConfig.put("customization", customization);

        // El payload debe contener el config completo — OnlyOffice verifica los tres campos
        Map<String, Object> payload = Map.of(
            "documentType", documentType,
            "document", documentMap,
            "editorConfig", editorConfig
        );

        String token = onlyOfficeJwtService.generarToken(payload);

        doc.getAuditLog().add(AuditEntry.builder()
            .usuarioId(usuarioId)
            .usuarioNombre(usuarioNombre)
            .accion(soloLectura ? "VER" : "EDITAR")
            .fecha(Instant.now())
            .detalle("Sesión OnlyOffice iniciada")
            .build());
        documentoRepository.save(doc);

        return new OnlyOfficeConfig(documentType, documentMap, editorConfig, token);
    }

    // --- Callback de OnlyOffice ---

    // status 2 = listo para guardar (todos cerraron)
    // status 6 = forcesave (guardado manual con Ctrl+S)
    public void procesarCallback(String id, Map<String, Object> body) {
        int status = ((Number) body.get("status")).intValue();
        if (status == 2 || status == 6) {
            String downloadUrl = (String) body.get("url");
            if (downloadUrl == null) return;

            Documento doc = obtener(id);

            try (InputStream is = URI.create(downloadUrl).toURL().openStream()) {
                minioService.putObjectFromStream(doc.getKey(), is, doc.getMimeType());
            } catch (Exception e) {
                throw new RuntimeException("Error guardando versión editada desde OnlyOffice: " + e.getMessage(), e);
            }

            // Nueva editorKey para que OnlyOffice no use la caché de la versión anterior
            doc.setEditorKey(UUID.randomUUID().toString().replace("-", "").substring(0, 20));
            doc.setUltimaEdicion(Instant.now());
            documentoRepository.save(doc);
        }
    }

    // --- Eliminar ---

    public void eliminar(String id, String usuarioId) {
        Documento doc = obtener(id);
        minioService.eliminar(doc.getKey());
        documentoRepository.deleteById(id);
    }

    // --- Permisos ---

    public Documento actualizarPermisos(String id, List<PermisoDocumento> permisos) {
        Documento doc = obtener(id);
        doc.setPermisos(permisos);
        return documentoRepository.save(doc);
    }

    // --- Helpers ---

    private String resolverNombre(String usuarioId) {
        return usuarioRepository.findById(usuarioId)
            .map(u -> u.getNombre() + " " + u.getApellido())
            .orElse("Usuario desconocido");
    }

    private String obtenerExtension(String nombre) {
        int dot = nombre.lastIndexOf('.');
        return dot >= 0 ? nombre.substring(dot + 1).toLowerCase() : "docx";
    }

    private String resolverTipoDocumento(String ext) {
        return switch (ext) {
            case "doc", "docx", "odt", "txt", "rtf" -> "word";
            case "xls", "xlsx", "ods", "csv"        -> "cell";
            case "ppt", "pptx", "odp"               -> "slide";
            case "pdf"                               -> "pdf";
            default                                  -> "word";
        };
    }
}
