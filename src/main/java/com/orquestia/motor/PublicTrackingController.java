package com.orquestia.motor;

import com.orquestia.instancia.InstanciaProceso;
import com.orquestia.instancia.InstanciaRepository;
import com.orquestia.instancia.TareaInstancia;
import com.orquestia.instancia.TareaRepository;
import com.orquestia.proceso.Proceso;
import com.orquestia.proceso.ProcesoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Endpoint público para tracking de instancias.
 * No requiere JWT — cualquier persona con el ID puede ver el progreso.
 * Devuelve solo datos no sensibles (sin formularios, sin asignaciones).
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicTrackingController {

    private final InstanciaRepository instanciaRepository;
    private final TareaRepository tareaRepository;
    private final ProcesoRepository procesoRepository;

    @GetMapping("/instancias/{id}")
    public ResponseEntity<InstanciaPublicaResponse> trackInstancia(@PathVariable String id) {
        InstanciaProceso instancia = instanciaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Instancia no encontrada"));

        String nombreProceso = procesoRepository.findById(instancia.getProcesoId())
                .map(Proceso::getNombre)
                .orElse("Proceso desconocido");

        List<TareaInstancia> tareas = tareaRepository.findByInstanciaIdOrderByFechaCreacionAsc(id);

        List<TimelineItem> timeline = tareas.stream()
                .map(t -> new TimelineItem(t.getNodoLabel(), t.getEstado(), t.getFechaCompletado()))
                .toList();

        return ResponseEntity.ok(new InstanciaPublicaResponse(
                instancia.getId(),
                nombreProceso,
                instancia.getEstado(),
                instancia.getFechaInicio(),
                instancia.getFechaFin(),
                timeline
        ));
    }

    record InstanciaPublicaResponse(
            String id,
            String nombreProceso,
            String estado,
            LocalDateTime fechaInicio,
            LocalDateTime fechaFin,
            List<TimelineItem> timeline
    ) {}

    record TimelineItem(
            String nodoLabel,
            String estado,
            LocalDateTime fechaCompletado
    ) {}
}
