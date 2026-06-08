package com.orquestia.prediccion;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Predicción de riesgo de demora de una instancia (Fase 5 — Deep Learning).
 *
 *   GET /api/instancias/{id}/prediccion
 *
 * Proxy al microservicio FastAPI: el backend calcula las features desde Mongo
 * y delega la inferencia del modelo TensorFlow + las recomendaciones.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PrediccionController {

    private final PrediccionService prediccionService;

    @GetMapping("/instancias/{id}/prediccion")
    public ResponseEntity<Map<String, Object>> predecir(@PathVariable String id) {
        return ResponseEntity.ok(prediccionService.predecir(id));
    }
}
