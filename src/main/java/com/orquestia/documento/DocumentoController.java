package com.orquestia.documento;

import com.orquestia.storage.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documentos")
@RequiredArgsConstructor
public class DocumentoController {

    private final DocumentoService documentoService;
    private final MinioService minioService;

    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    // Iniciar subida — devuelve uploadUrl presignada + documentoId
    @PostMapping("/iniciar-upload")
    public ResponseEntity<DocumentoService.IniciarUploadResponse> iniciarUpload(
            @RequestBody DocumentoService.IniciarUploadRequest req,
            Authentication auth) {
        return ResponseEntity.ok(documentoService.iniciarUpload(req, auth.getName()));
    }

    // Todos los documentos de una empresa (para el SGD — solo ADMIN)
    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<List<Documento>> listarPorEmpresa(@PathVariable String empresaId) {
        return ResponseEntity.ok(documentoService.listarPorEmpresa(empresaId));
    }

    // Documentos del funcionario autenticado: solo instancias donde tiene tareas asignadas
    @GetMapping("/mis-documentos")
    public ResponseEntity<List<Documento>> misDocumentos(Authentication auth) {
        return ResponseEntity.ok(documentoService.listarParaFuncionario(auth.getName()));
    }

    // Documentos de una instancia específica
    @GetMapping("/instancia/{instanciaId}")
    public ResponseEntity<List<Documento>> listarPorInstancia(@PathVariable String instanciaId) {
        return ResponseEntity.ok(documentoService.listarPorInstancia(instanciaId));
    }

    // Obtener un documento por id
    @GetMapping("/{id}")
    public ResponseEntity<Documento> obtener(@PathVariable String id) {
        return ResponseEntity.ok(documentoService.obtener(id));
    }

    // URL temporal de descarga
    @GetMapping("/{id}/descargar")
    public ResponseEntity<Map<String, String>> descargar(
            @PathVariable String id,
            Authentication auth) {
        String url = documentoService.generarUrlDescarga(id, auth.getName());
        return ResponseEntity.ok(Map.of("url", url));
    }

    // Config de OnlyOffice para abrir el editor
    @GetMapping("/{id}/editor-config")
    public ResponseEntity<DocumentoService.OnlyOfficeConfig> editorConfig(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean soloLectura,
            Authentication auth) {
        DocumentoService.OnlyOfficeConfig config = documentoService.generarEditorConfig(
            id, backendUrl, auth.getName(), auth.getName(), soloLectura
        );
        return ResponseEntity.ok(config);
    }

    // Debug: devuelve el JWT crudo para verificarlo en jwt.io
    @GetMapping("/{id}/debug-token")
    public ResponseEntity<Map<String, Object>> debugToken(
            @PathVariable String id,
            Authentication auth) {
        DocumentoService.OnlyOfficeConfig config = documentoService.generarEditorConfig(
            id, backendUrl, auth.getName(), auth.getName(), false
        );
        return ResponseEntity.ok(Map.of(
            "token", config.token(),
            "documentType", config.documentType(),
            "document", config.document(),
            "editorConfig", config.editorConfig()
        ));
    }

    // Proxy stream para OnlyOffice — sin auth, OnlyOffice descarga el archivo aquí
    @GetMapping("/{id}/stream")
    public void stream(@PathVariable String id, HttpServletResponse response) throws IOException {
        Documento doc = documentoService.obtener(id);
        response.setContentType(doc.getMimeType() != null ? doc.getMimeType() : "application/octet-stream");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getNombre() + "\"");
        try (InputStream is = minioService.getObjectStream(doc.getKey())) {
            is.transferTo(response.getOutputStream());
        }
    }

    // Callback de OnlyOffice (sin auth — OnlyOffice llama directamente)
    @PostMapping("/{id}/callback")
    public ResponseEntity<Map<String, Integer>> callback(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        documentoService.procesarCallback(id, body);
        return ResponseEntity.ok(Map.of("error", 0));
    }

    // Eliminar documento
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @PathVariable String id,
            Authentication auth) {
        documentoService.eliminar(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    // Actualizar permisos
    @PutMapping("/{id}/permisos")
    public ResponseEntity<Documento> actualizarPermisos(
            @PathVariable String id,
            @RequestBody List<PermisoDocumento> permisos) {
        return ResponseEntity.ok(documentoService.actualizarPermisos(id, permisos));
    }

    // El admin concede edición a un funcionario sobre este documento
    @PostMapping("/{id}/permisos/conceder")
    public ResponseEntity<Documento> concederEdicion(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        return ResponseEntity.ok(
            documentoService.concederEdicion(id, body.get("usuarioId"), auth.getName()));
    }

    // El admin revoca la edición concedida a un funcionario
    @DeleteMapping("/{id}/permisos/{usuarioId}")
    public ResponseEntity<Documento> revocarEdicion(
            @PathVariable String id,
            @PathVariable String usuarioId,
            Authentication auth) {
        return ResponseEntity.ok(
            documentoService.revocarEdicion(id, usuarioId, auth.getName()));
    }
}
