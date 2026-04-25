package com.orquestia.proceso;

import com.orquestia.instancia.InstanciaRepository;
import com.orquestia.notificacion.NotificacionService;
import com.orquestia.proceso.dto.ProcesoRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProcesoService {

    private final ProcesoRepository procesoRepository;
    private final InstanciaRepository instanciaRepository;
    private final NotificacionService notificacionService;
    private final SimpMessagingTemplate ws;

    /**
     * Crea un nuevo proceso en estado BORRADOR.
     */
    public Proceso crearProceso(ProcesoRequest request, String userId) {
        Proceso proceso = Proceso.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .empresaId(request.getEmpresaId())
                .creadoPor(userId)
                .build();
        return procesoRepository.save(proceso);
    }

    /**
     * Lista procesos de una empresa, opcionalmente filtrados por estado.
     */
    public List<Proceso> listarProcesos(String empresaId, String estado) {
        if (estado != null && !estado.isEmpty()) {
            return procesoRepository.findByEmpresaIdAndEstado(empresaId, estado);
        }
        return procesoRepository.findByEmpresaId(empresaId);
    }

    /**
     * Obtiene un proceso por ID.
     */
    public Proceso obtenerProceso(String id) {
        return procesoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado con id: " + id));
    }

    /**
     * Actualiza nombre, descripción y el grafo (nodos + conexiones).
     * Este es el endpoint que usa el diagramador cuando guarda.
     */
    public Proceso actualizarProceso(String id, ProcesoRequest request) {
        Proceso proceso = obtenerProceso(id);

        // Solo permite editar si está en BORRADOR
        if (!"BORRADOR".equals(proceso.getEstado())) {
            throw new RuntimeException("Solo se pueden editar procesos en estado BORRADOR");
        }

        proceso.setNombre(request.getNombre());
        proceso.setDescripcion(request.getDescripcion());

        if (request.getNodos() != null) {
            proceso.setNodos(request.getNodos());
        }
        if (request.getConexiones() != null) {
            proceso.setConexiones(request.getConexiones());
        }
        if (request.getAsignaciones() != null) {
            proceso.setAsignaciones(request.getAsignaciones());
        }

        return procesoRepository.save(proceso);
    }

    /**
     * Publica un proceso — lo hace disponible para crear instancias.
     * Valida que tenga al menos un nodo INICIO y un nodo FIN.
     */
    public Proceso publicarProceso(String id) {
        Proceso proceso = obtenerProceso(id);

        if (!"BORRADOR".equals(proceso.getEstado())) {
            throw new RuntimeException("Solo se pueden publicar procesos en estado BORRADOR");
        }

        // Validaciones mínimas
        boolean tieneInicio = proceso.getNodos().stream()
                .anyMatch(n -> "INICIO".equals(n.getTipo()));
        boolean tieneFin = proceso.getNodos().stream()
                .anyMatch(n -> "FIN".equals(n.getTipo()));

        if (!tieneInicio) {
            throw new RuntimeException("El proceso debe tener al menos un nodo de INICIO");
        }
        if (!tieneFin) {
            throw new RuntimeException("El proceso debe tener al menos un nodo de FIN");
        }
        if (proceso.getConexiones().isEmpty()) {
            throw new RuntimeException("El proceso debe tener al menos una conexion");
        }

        proceso.setEstado("PUBLICADO");
        proceso.setVersion(proceso.getVersion() + 1);
        return procesoRepository.save(proceso);
    }

    /**
     * Archiva un proceso — ya no se pueden crear nuevas instancias.
     */
    public Proceso archivarProceso(String id) {
        Proceso proceso = obtenerProceso(id);
        proceso.setEstado("ARCHIVADO");
        return procesoRepository.save(proceso);
    }

    /**
     * Crea una nueva versión editable (BORRADOR) copiando un proceso PUBLICADO.
     * El proceso original queda ARCHIVADO; las instancias activas no se ven afectadas
     * porque el motor las localiza por procesoId, no por estado.
     */
    public Proceso crearNuevaVersion(String id) {
        Proceso viejo = obtenerProceso(id);
        if (!"PUBLICADO".equals(viejo.getEstado())) {
            throw new RuntimeException("Solo se puede crear nueva versión de un proceso PUBLICADO");
        }

        // Archivar el proceso actual
        viejo.setEstado("ARCHIVADO");
        procesoRepository.save(viejo);

        // Crear copia en BORRADOR con la misma versión (se incrementará al publicar)
        Proceso nuevo = Proceso.builder()
                .nombre(viejo.getNombre())
                .descripcion(viejo.getDescripcion())
                .empresaId(viejo.getEmpresaId())
                .creadoPor(viejo.getCreadoPor())
                .nodos(new java.util.ArrayList<>(viejo.getNodos()))
                .conexiones(new java.util.ArrayList<>(viejo.getConexiones()))
                .asignaciones(viejo.getAsignaciones() != null
                        ? new java.util.HashMap<>(viejo.getAsignaciones()) : new java.util.HashMap<>())
                .version(viejo.getVersion())
                .build();

        return procesoRepository.save(nuevo);
    }

    /**
     * Elimina un proceso. Falla con 409 si existen instancias activas.
     */
    public void eliminarProceso(String id) {
        if (!instanciaRepository.findByProcesoIdAndEstado(id, "ACTIVA").isEmpty()) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "No se puede eliminar un proceso con instancias activas");
        }
        procesoRepository.deleteById(id);
    }

    /**
     * Devuelve los procesos PUBLICADOS donde el userId está asignado al departamento
     * del primer nodo ACTIVIDAD. Usado por funcionarios para saber qué procesos pueden iniciar.
     */
    public List<Proceso> listarIniciables(String empresaId, String userId) {
        return procesoRepository.findByEmpresaIdAndEstado(empresaId, "PUBLICADO")
                .stream()
                .filter(p -> esIniciadoPor(p, userId))
                .collect(java.util.stream.Collectors.toList());
    }

    /** Guarda las asignaciones (departamentoId → userId) y notifica a cada funcionario asignado. */
    public Proceso guardarAsignaciones(String procesoId, Map<String, String> asignaciones) {
        Proceso proceso = obtenerProceso(procesoId);
        Map<String, String> anteriores = proceso.getAsignaciones() != null
                ? proceso.getAsignaciones() : Map.of();

        proceso.setAsignaciones(asignaciones);
        Proceso guardado = procesoRepository.save(proceso);

        // Notificar y avisar por WS solo a usuarios recién asignados
        asignaciones.forEach((deptId, userId) -> {
            if (userId != null && !userId.equals(anteriores.get(deptId))) {
                Map<String, Object> payload = Map.of(
                        "tipo", "PROCESO_ASIGNADO",
                        "procesoId", procesoId,
                        "procesoNombre", proceso.getNombre()
                );
                notificacionService.crear(userId, "PROCESO_ASIGNADO",
                        "Has sido asignado al proceso: " + proceso.getNombre(),
                        payload
                );
                ws.convertAndSend("/topic/usuario/" + userId, payload);
            }
        });

        return guardado;
    }

    private boolean esIniciadoPor(Proceso proceso, String userId) {
        return proceso.getNodos().stream()
                .filter(n -> "INICIO".equals(n.getTipo()))
                .findFirst()
                .flatMap(inicio -> proceso.getConexiones().stream()
                        .filter(c -> c.getOrigenId().equals(inicio.getId()))
                        .findFirst())
                .flatMap(conn -> proceso.getNodos().stream()
                        .filter(n -> n.getId().equals(conn.getDestinoId())
                                && "ACTIVIDAD".equals(n.getTipo()))
                        .findFirst())
                .map(n -> userId.equals(proceso.getAsignaciones().get(n.getDepartamentoId())))
                .orElse(false);
    }
}
