package com.orquestia.prediccion;

import com.orquestia.auth.Usuario;
import com.orquestia.auth.UsuarioRepository;
import com.orquestia.instancia.InstanciaProceso;
import com.orquestia.instancia.InstanciaRepository;
import com.orquestia.instancia.TareaInstancia;
import com.orquestia.instancia.TareaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Predicción de riesgo de demora de una instancia (Fase 5 — Deep Learning).
 *
 * Aquí se CALCULAN las features (porque los datos viven en Mongo) y se delega
 * la inferencia + el lenguaje natural al microservicio FastAPI, que corre el
 * modelo TensorFlow. Las features deben construirse EXACTAMENTE como en el
 * entrenamiento (ver app/ml/modelo_bpm.py en el servicio IA):
 *   hora_creacion, dia_semana(0=lunes), carga_funcionario,
 *   duracion_historica_avg (min), intentos.
 */
@Slf4j
@Service
public class PrediccionService {

    private static final List<String> ABIERTAS = List.of("PENDIENTE", "EN_PROGRESO");
    private static final double DURACION_DEFAULT = 60.0;

    private final InstanciaRepository instanciaRepository;
    private final TareaRepository tareaRepository;
    private final UsuarioRepository usuarioRepository;
    private final RestClient iaClient;
    private final String iaServiceUrl;

    public PrediccionService(InstanciaRepository instanciaRepository,
                             TareaRepository tareaRepository,
                             UsuarioRepository usuarioRepository,
                             @Value("${ia.service.url}") String iaServiceUrl) {
        this.instanciaRepository = instanciaRepository;
        this.tareaRepository = tareaRepository;
        this.usuarioRepository = usuarioRepository;
        // Guardamos la URL (sin validarla aquí) y la usamos completa en cada request.
        // Así una URL mal configurada solo falla la predicción (502), no tumba el
        // arranque del backend completo. .trim() defiende de espacios/saltos de línea.
        this.iaServiceUrl = iaServiceUrl != null ? iaServiceUrl.trim() : "";
        // Forzamos HTTP/1.1: el HttpClient de Java intenta por defecto un upgrade a
        // HTTP/2 (h2c) que uvicorn no soporta y que hace que se pierda el body (422).
        HttpClient http11 = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.iaClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(http11))
                .build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> predecir(String instanciaId) {
        InstanciaProceso inst = instanciaRepository.findById(instanciaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Instancia no encontrada: " + instanciaId));

        String empresaId = inst.getEmpresaId();

        // Universo de tareas de la empresa (para carga e histórico)
        List<String> instanciaIds = instanciaRepository.findByEmpresaId(empresaId)
                .stream().map(InstanciaProceso::getId).collect(Collectors.toList());
        List<TareaInstancia> universo = instanciaIds.isEmpty()
                ? List.of() : tareaRepository.findByInstanciaIdIn(instanciaIds);

        // Carga actual por funcionario: tareas abiertas asignadas
        Map<String, Long> cargaPorUsuario = universo.stream()
                .filter(t -> ABIERTAS.contains(t.getEstado()) && t.getAsignadoA() != null)
                .collect(Collectors.groupingBy(TareaInstancia::getAsignadoA, Collectors.counting()));

        // Duración histórica promedio (min) por etiqueta de nodo (tareas completadas)
        Map<String, Double> histPorNodo = universo.stream()
                .filter(t -> "COMPLETADA".equals(t.getEstado())
                        && t.getFechaCreacion() != null && t.getFechaCompletado() != null
                        && t.getNodoLabel() != null)
                .collect(Collectors.groupingBy(TareaInstancia::getNodoLabel,
                        Collectors.averagingDouble(t ->
                                Duration.between(t.getFechaCreacion(), t.getFechaCompletado()).toMinutes())));
        double durGlobal = histPorNodo.values().stream()
                .mapToDouble(Double::doubleValue).average().orElse(DURACION_DEFAULT);

        // Tareas pendientes de ESTA instancia
        List<TareaInstancia> pendientes = tareaRepository.findByInstanciaId(instanciaId).stream()
                .filter(t -> ABIERTAS.contains(t.getEstado()))
                .collect(Collectors.toList());

        Map<String, String> nombres = nombresUsuarios(pendientes.stream()
                .map(TareaInstancia::getAsignadoA).filter(a -> a != null).collect(Collectors.toSet()));

        List<Map<String, Object>> tareas = new ArrayList<>();
        for (TareaInstancia t : pendientes) {
            LocalDateTime fc = t.getFechaCreacion() != null ? t.getFechaCreacion() : LocalDateTime.now();
            long carga = t.getAsignadoA() != null ? cargaPorUsuario.getOrDefault(t.getAsignadoA(), 0L) : 0L;
            double durHist = histPorNodo.getOrDefault(t.getNodoLabel(), durGlobal);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tarea_id", t.getId());
            m.put("nodo_label", t.getNodoLabel() != null ? t.getNodoLabel() : "");
            m.put("funcionario", t.getAsignadoA() != null
                    ? nombres.getOrDefault(t.getAsignadoA(), "") : "");
            m.put("hora_creacion", fc.getHour());
            m.put("dia_semana", fc.getDayOfWeek().getValue() - 1); // 0=lunes ... 6=domingo
            m.put("carga_funcionario", (int) carga);
            m.put("duracion_historica_avg", Math.round(durHist * 10.0) / 10.0);
            m.put("intentos", t.getIntentos());
            tareas.add(m);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instancia_id", instanciaId);
        body.put("proceso_nombre", inst.getProcesoNombre());
        body.put("tareas", tareas);

        try {
            return iaClient.post()
                    .uri(iaServiceUrl + "/ia/predecir-instancia")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("Error llamando al servicio de predicción IA: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo obtener la predicción del servicio de IA. ¿Está corriendo el microservicio?");
        }
    }

    private Map<String, String> nombresUsuarios(Collection<String> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<String, String> map = new HashMap<>();
        for (Usuario u : usuarioRepository.findAllById(ids)) {
            map.put(u.getId(), (u.getNombre() + " " + u.getApellido()).trim());
        }
        return map;
    }
}
