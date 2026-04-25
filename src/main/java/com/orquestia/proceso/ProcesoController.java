package com.orquestia.proceso;

import com.orquestia.proceso.dto.ProcesoRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller REST para gestión de Procesos (workflows).
 *
 * Endpoints:
 *   POST   /api/procesos                   → Crear proceso (en BORRADOR)
 *   GET    /api/procesos?empresaId=X&estado=Y  → Listar procesos
 *   GET    /api/procesos/{id}              → Obtener proceso con nodos y conexiones
 *   PUT    /api/procesos/{id}              → Actualizar proceso (guardar diagrama)
 *   POST   /api/procesos/{id}/publicar     → Publicar proceso
 *   POST   /api/procesos/{id}/archivar     → Archivar proceso
 *   DELETE /api/procesos/{id}              → Eliminar proceso (solo BORRADOR)
 */
@RestController
@RequestMapping("/api/procesos")
@RequiredArgsConstructor
public class ProcesoController {

    private final ProcesoService procesoService;

    @PostMapping
    public ResponseEntity<Proceso> crearProceso(
            @Valid @RequestBody ProcesoRequest request,
            Authentication auth) {
        String userId = auth.getName();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(procesoService.crearProceso(request, userId));
    }

    @GetMapping
    public ResponseEntity<List<Proceso>> listarProcesos(
            @RequestParam String empresaId,
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(procesoService.listarProcesos(empresaId, estado));
    }

    /** Procesos que puede iniciar el usuario autenticado (según asignaciones) */
    @GetMapping("/iniciables")
    public ResponseEntity<List<Proceso>> listarIniciables(
            @RequestParam String empresaId,
            Authentication auth) {
        return ResponseEntity.ok(procesoService.listarIniciables(empresaId, auth.getName()));
    }

    /** Guarda las asignaciones (departamentoId → userId) de un proceso */
    @PutMapping("/{id}/asignaciones")
    public ResponseEntity<Proceso> guardarAsignaciones(
            @PathVariable String id,
            @RequestBody Map<String, String> asignaciones) {
        return ResponseEntity.ok(procesoService.guardarAsignaciones(id, asignaciones));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Proceso> obtenerProceso(@PathVariable String id) {
        return ResponseEntity.ok(procesoService.obtenerProceso(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Proceso> actualizarProceso(
            @PathVariable String id,
            @Valid @RequestBody ProcesoRequest request) {
        return ResponseEntity.ok(procesoService.actualizarProceso(id, request));
    }

    @PostMapping("/{id}/publicar")
    public ResponseEntity<Proceso> publicarProceso(@PathVariable String id) {
        return ResponseEntity.ok(procesoService.publicarProceso(id));
    }

    @PostMapping("/{id}/archivar")
    public ResponseEntity<Proceso> archivarProceso(@PathVariable String id) {
        return ResponseEntity.ok(procesoService.archivarProceso(id));
    }

    /**
     * POST /api/procesos/{id}/nueva-version
     * Archiva el proceso PUBLICADO y devuelve una copia en BORRADOR lista para editar.
     */
    @PostMapping("/{id}/nueva-version")
    public ResponseEntity<Proceso> crearNuevaVersion(@PathVariable String id) {
        return ResponseEntity.ok(procesoService.crearNuevaVersion(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarProceso(@PathVariable String id) {
        procesoService.eliminarProceso(id);
        return ResponseEntity.noContent().build();
    }
}
