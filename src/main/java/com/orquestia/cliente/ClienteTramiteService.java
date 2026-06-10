package com.orquestia.cliente;

import com.orquestia.auth.Usuario;
import com.orquestia.auth.UsuarioRepository;
import com.orquestia.documento.DocumentoService;
import com.orquestia.instancia.InstanciaProceso;
import com.orquestia.instancia.InstanciaRepository;
import com.orquestia.instancia.TareaInstancia;
import com.orquestia.instancia.TareaRepository;
import com.orquestia.motor.MotorBPMService;
import com.orquestia.proceso.CampoFormulario;
import com.orquestia.proceso.Proceso;
import com.orquestia.proceso.ProcesoRepository;
import com.orquestia.proceso.RequisitoDocumento;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orquesta el inicio de un trámite por parte de un CLIENTE.
 *
 * A diferencia de iniciar un proceso como funcionario/admin, aquí:
 *   1. Se valida que el proceso esté habilitado para clientes.
 *   2. Se exige que estén presentes todos los documentos requeridos obligatorios
 *      (subidos por el cliente ANTES de crear la instancia).
 *   3. Se crea la instancia con clienteId = el cliente autenticado.
 *   4. Se re-vinculan esos documentos de entrada a la instancia recién creada.
 */
@Service
@RequiredArgsConstructor
public class ClienteTramiteService {

    private final ProcesoRepository procesoRepository;
    private final MotorBPMService motor;
    private final DocumentoService documentoService;
    private final InstanciaRepository instanciaRepository;
    private final TareaRepository tareaRepository;
    private final UsuarioRepository usuarioRepository;

    /** Trámites iniciados por el cliente, del más reciente al más antiguo. */
    public List<InstanciaProceso> misTramites(String clienteId) {
        return instanciaRepository.findByClienteIdOrderByFechaInicioDesc(clienteId);
    }

    /** Todos los documentos que le pertenecen al cliente (subidos por él o de sus trámites). */
    public List<com.orquestia.documento.Documento> misDocumentos(String clienteId) {
        return documentoService.listarPorCliente(clienteId);
    }

    /** Acciones (tareas de autoservicio) pendientes asignadas al cliente. */
    public List<TareaInstancia> misAcciones(String clienteId) {
        return tareaRepository.findByAsignadoAAndEstadoIn(
            clienteId, Arrays.asList("PENDIENTE", "EN_PROGRESO"));
    }

    /**
     * Detalle de UN trámite del cliente, incluyendo cómo cada funcionario llenó los
     * formularios de cada actividad (como lo ve el admin en Ejecuciones).
     * Verifica que el trámite le pertenezca al cliente autenticado.
     */
    public TramiteDetalleResponse detalle(String clienteId, String instanciaId) {
        InstanciaProceso inst = instanciaRepository.findById(instanciaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trámite no encontrado"));
        if (!clienteId.equals(inst.getClienteId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este trámite no te pertenece");
        }

        String procesoNombre = inst.getProcesoNombre() != null && !inst.getProcesoNombre().isBlank()
            ? inst.getProcesoNombre()
            : procesoRepository.findById(inst.getProcesoId()).map(Proceso::getNombre).orElse(inst.getProcesoId());

        List<TareaInstancia> tareas = tareaRepository.findByInstanciaIdOrderByFechaCreacionAsc(instanciaId);

        // Resolver nombres de los funcionarios que ejecutaron cada tarea
        Set<String> userIds = tareas.stream()
            .map(TareaInstancia::getAsignadoA).filter(a -> a != null).collect(Collectors.toSet());
        Map<String, String> nombres = new HashMap<>();
        for (Usuario u : usuarioRepository.findAllById(userIds)) {
            nombres.put(u.getId(), (u.getNombre() + " " + u.getApellido()).trim());
        }

        List<TareaDetalle> detalles = tareas.stream().map(t -> new TareaDetalle(
            t.getNodoLabel(),
            t.getEstado(),
            t.getAsignadoA() != null ? nombres.getOrDefault(t.getAsignadoA(), "Funcionario") : null,
            t.getFechaCreacion(),
            t.getFechaCompletado(),
            t.getFormularioCampos(),
            "COMPLETADA".equals(t.getEstado()) ? t.getDatos() : Map.of() // solo mostrar datos de tareas completadas
        )).toList();

        return new TramiteDetalleResponse(
            inst.getId(), procesoNombre, inst.getEstado(),
            inst.getFechaInicio(), inst.getFechaFin(), detalles);
    }

    public record TramiteDetalleResponse(
        String id,
        String procesoNombre,
        String estado,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        List<TareaDetalle> tareas
    ) {}

    public record TareaDetalle(
        String nodoLabel,
        String estado,
        String ejecutadoPor,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaCompletado,
        List<CampoFormulario> formularioCampos,
        Map<String, Object> datos
    ) {}

    /**
     * El cliente completa una de SUS acciones. Verifica que la tarea le pertenezca
     * antes de delegar al motor (que avanza el proceso igual que con un funcionario).
     */
    public TareaInstancia completarAccion(String clienteId, String tareaId,
                                          Map<String, Object> datos, String comentario) {
        TareaInstancia tarea = tareaRepository.findById(tareaId)
            .orElseThrow(() -> new RuntimeException("Acción no encontrada: " + tareaId));
        if (!clienteId.equals(tarea.getAsignadoA())) {
            throw new RuntimeException("Esta acción no pertenece al cliente");
        }
        return motor.completarTarea(tareaId, datos, comentario);
    }

    public InstanciaProceso iniciarTramite(String clienteId, String procesoId,
                                           List<String> documentoIds,
                                           Map<String, Object> variables) {
        Proceso proceso = procesoRepository.findById(procesoId)
            .orElseThrow(() -> new RuntimeException("Proceso no encontrado: " + procesoId));

        if (!proceso.isHabilitadoParaClientes()) {
            throw new RuntimeException("Este proceso no está habilitado para clientes");
        }
        if (!"PUBLICADO".equals(proceso.getEstado())) {
            throw new RuntimeException("Solo se pueden iniciar procesos PUBLICADOS");
        }

        // Validar documentos obligatorios
        long obligatorios = proceso.getDocumentosRequeridos().stream()
            .filter(RequisitoDocumento::isObligatorio)
            .count();
        int aportados = documentoIds == null ? 0 : documentoIds.size();
        if (aportados < obligatorios) {
            throw new RuntimeException(
                "Faltan documentos obligatorios: se requieren " + obligatorios +
                " y se aportaron " + aportados);
        }

        // Crear la instancia con clienteId y vincular los documentos de entrada
        InstanciaProceso instancia = motor.iniciarProceso(procesoId, clienteId, clienteId, variables);
        documentoService.vincularAInstancia(documentoIds, instancia.getId(), procesoId, clienteId);

        return instancia;
    }
}
