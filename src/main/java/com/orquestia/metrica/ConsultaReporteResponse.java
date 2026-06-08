package com.orquestia.metrica;

import java.util.List;

/**
 * Resultado tabular uniforme de un reporte personalizado.
 * El frontend lo renderiza como tabla y lo exporta a PDF/Excel sin lógica extra.
 */
public record ConsultaReporteResponse(
        String titulo,
        String metrica,
        List<String> columnas,
        List<List<Object>> filas,
        long total
) {}
