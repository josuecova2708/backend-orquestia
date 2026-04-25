package com.orquestia.motor;

import com.orquestia.instancia.InstanciaProceso;
import com.orquestia.instancia.TareaInstancia;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API del Motor BPM.
 *
 * Flujo de uso típico:
 *   1. POST /api/instancias                    → Iniciar proceso
 *   2. GET  /api/mis-tareas?departamentoId=X   → Ver tareas pendientes
 *   3. PUT  /api/tareas/{id}/iniciar           → Marcar como "en progreso"
 *   4. PUT  /api/tareas/{id}/completar         → Completar con datos del formulario
 *   5. GET  /api/instancias/{id}               → Ver estado actual de la ejecución
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MotorController {

    private final MotorBPMService motor;

    // =========================================================================
    // INSTANCIAS
    // =========================================================================

    /**
     * POST /api/instancias
     * Body: { "procesoId": "...", "variables": {} }
     * Inicia la ejecución de un proceso publicado.
     */
    @PostMapping("/instancias")
    public ResponseEntity<InstanciaProceso> iniciarProceso(@RequestBody IniciarProcesoRequest req,
                                                            Authentication auth) {
        return ResponseEntity.ok(motor.iniciarProceso(req.procesoId(), auth.getName(), req.variables()));
    }

    /**
     * GET /api/instancias/{id}
     * Estado actual de una ejecución.
     */
    @GetMapping("/instancias/{id}")
    public ResponseEntity<InstanciaProceso> obtenerInstancia(@PathVariable String id) {
        return ResponseEntity.ok(motor.obtenerInstancia(id));
    }

    /**
     * GET /api/instancias/{id}/tareas
     * Todas las tareas (en todos los estados) de una ejecución — para auditoria/historial.
     */
    @GetMapping("/instancias/{id}/tareas")
    public ResponseEntity<List<TareaInstancia>> obtenerTareas(@PathVariable String id) {
        return ResponseEntity.ok(motor.obtenerTareasDeInstancia(id));
    }

    /**
     * GET /api/instancias?empresaId=xxx[&estado=ACTIVA]
     * Lista instancias de una empresa. Sin estado → todas. Con estado → filtradas.
     */
    @GetMapping("/instancias")
    public ResponseEntity<List<InstanciaProceso>> listarInstancias(
            @RequestParam String empresaId,
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(motor.listarInstancias(empresaId, estado));
    }

    /**
     * DELETE /api/instancias/{id}
     * Cancela una instancia y rechaza todas sus tareas abiertas.
     */
    @DeleteMapping("/instancias/{id}")
    public ResponseEntity<Void> cancelarInstancia(@PathVariable String id) {
        motor.cancelarInstancia(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // TAREAS
    // =========================================================================

    /**
     * GET /api/mis-tareas
     * Bandeja de entrada del funcionario: tareas PENDIENTES/EN_PROGRESO asignadas a él.
     */
    @GetMapping("/mis-tareas")
    public ResponseEntity<List<TareaInstancia>> obtenerMisTareas(Authentication auth) {
        return ResponseEntity.ok(motor.obtenerMisTareas(auth.getName()));
    }

    /**
     * GET /api/mis-instancias
     * Historial del funcionario: todas las instancias en las que participó.
     */
    @GetMapping("/mis-instancias")
    public ResponseEntity<List<InstanciaProceso>> obtenerMisInstancias(Authentication auth) {
        return ResponseEntity.ok(motor.obtenerMisInstancias(auth.getName()));
    }

    /**
     * PUT /api/tareas/{id}/iniciar
     * Marcar la tarea como "Estoy trabajando en esto".
     */
    @PutMapping("/tareas/{id}/iniciar")
    public ResponseEntity<TareaInstancia> iniciarTarea(@PathVariable String id) {
        return ResponseEntity.ok(motor.iniciarTrabajo(id));
    }

    /**
     * PUT /api/tareas/{id}/completar
     * El funcionario entrega los datos del formulario y el motor avanza el proceso.
     *
     * Body: {
     *   "datos": { "decision": "Aprobado", "puntaje_buro": 720 },
     *   "comentario": "Cliente con buen historial"
     * }
     */
    @PutMapping("/tareas/{id}/completar")
    public ResponseEntity<TareaInstancia> completarTarea(@PathVariable String id,
                                                          @RequestBody CompletarTareaRequest req) {
        return ResponseEntity.ok(motor.completarTarea(id, req.datos(), req.comentario()));
    }

    // =========================================================================
    // DTOs (Records internos de Java — limpio y sin archivos extra)
    // =========================================================================

    record IniciarProcesoRequest(String procesoId, Map<String, Object> variables) {}
    record CompletarTareaRequest(Map<String, Object> datos, String comentario) {}
}
