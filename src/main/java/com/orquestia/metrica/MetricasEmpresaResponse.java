package com.orquestia.metrica;

import java.util.List;
import java.util.Map;

public record MetricasEmpresaResponse(
        Map<String, Long> instanciasPorEstado,
        List<CuelloBotella> cuellosBottela,
        List<CargaFuncionario> cargaFuncionarios,
        List<ActividadDia> actividadReciente
) {
    public record CuelloBotella(String nodoLabel, double avgMinutos, long total) {}
    public record CargaFuncionario(String userId, long pendientes) {}
    public record ActividadDia(String fecha, long total) {}
}
