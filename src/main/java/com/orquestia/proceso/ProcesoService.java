package com.orquestia.proceso;

import com.orquestia.proceso.dto.ProcesoRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcesoService {

    private final ProcesoRepository procesoRepository;

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
     * Elimina un proceso.
     */
    public void eliminarProceso(String id) {
        procesoRepository.deleteById(id);
    }
}
