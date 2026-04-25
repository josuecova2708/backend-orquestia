package com.orquestia.metrica;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/metricas")
@RequiredArgsConstructor
public class MetricaController {

    private final MetricaService metricaService;

    @GetMapping
    public ResponseEntity<MetricasEmpresaResponse> getMetricas(@RequestParam String empresaId) {
        return ResponseEntity.ok(metricaService.calcular(empresaId));
    }
}
