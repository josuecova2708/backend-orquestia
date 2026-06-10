package com.orquestia.cliente;

import com.orquestia.instancia.InstanciaProceso;
import com.orquestia.instancia.TareaInstancia;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API para que un CLIENTE inicie trámites desde su portal.
 */
@RestController
@RequestMapping("/api/cliente")
@RequiredArgsConstructor
public class ClienteTramiteController {

    private final ClienteTramiteService clienteTramiteService;

    /**
     * POST /api/cliente/iniciar-tramite
     * Body: { "procesoId": "...", "documentoIds": ["..."], "variables": {} }
     * Crea una instancia a nombre del cliente autenticado, validando los
     * documentos requeridos y vinculándolos al trámite.
     */
    @PostMapping("/iniciar-tramite")
    public ResponseEntity<InstanciaProceso> iniciarTramite(
            @RequestBody IniciarTramiteRequest req,
            Authentication auth) {
        return ResponseEntity.ok(clienteTramiteService.iniciarTramite(
            auth.getName(), req.procesoId(), req.documentoIds(), req.variables()));
    }

    /**
     * GET /api/cliente/mis-tramites
     * Lista los trámites del cliente autenticado (los que él inició).
     */
    @GetMapping("/mis-tramites")
    public ResponseEntity<List<InstanciaProceso>> misTramites(Authentication auth) {
        return ResponseEntity.ok(clienteTramiteService.misTramites(auth.getName()));
    }

    /**
     * GET /api/cliente/mis-documentos
     * Todos los documentos que le pertenecen al cliente autenticado.
     */
    @GetMapping("/mis-documentos")
    public ResponseEntity<List<com.orquestia.documento.Documento>> misDocumentos(Authentication auth) {
        return ResponseEntity.ok(clienteTramiteService.misDocumentos(auth.getName()));
    }

    /**
     * GET /api/cliente/tramites/{id}/detalle
     * Detalle del trámite con los formularios llenados por cada funcionario.
     * Solo accesible por el cliente dueño del trámite.
     */
    @GetMapping("/tramites/{id}/detalle")
    public ResponseEntity<ClienteTramiteService.TramiteDetalleResponse> detalleTramite(
            @PathVariable String id,
            Authentication auth) {
        return ResponseEntity.ok(clienteTramiteService.detalle(auth.getName(), id));
    }

    /**
     * GET /api/cliente/mis-acciones
     * Tareas de autoservicio pendientes asignadas al cliente (aceptar, firmar, etc.).
     */
    @GetMapping("/mis-acciones")
    public ResponseEntity<List<TareaInstancia>> misAcciones(Authentication auth) {
        return ResponseEntity.ok(clienteTramiteService.misAcciones(auth.getName()));
    }

    /**
     * POST /api/cliente/acciones/{tareaId}/completar
     * El cliente completa su acción; el motor avanza el proceso.
     */
    @PostMapping("/acciones/{tareaId}/completar")
    public ResponseEntity<TareaInstancia> completarAccion(
            @PathVariable String tareaId,
            @RequestBody CompletarAccionRequest req,
            Authentication auth) {
        return ResponseEntity.ok(clienteTramiteService.completarAccion(
            auth.getName(), tareaId, req.datos(), req.comentario()));
    }

    record CompletarAccionRequest(
        Map<String, Object> datos,
        String comentario
    ) {}

    record IniciarTramiteRequest(
        String procesoId,
        List<String> documentoIds,
        Map<String, Object> variables
    ) {}
}
