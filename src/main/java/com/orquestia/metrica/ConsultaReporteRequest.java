package com.orquestia.metrica;

/**
 * Petición de un reporte personalizado generado a partir de una consulta
 * en lenguaje natural ya interpretada por el agente IA.
 *
 * El agente traduce la consulta del usuario a esta especificación estructurada:
 * una métrica del catálogo fijo + filtros opcionales. El backend la calcula
 * y devuelve una tabla uniforme (columnas + filas).
 */
public record ConsultaReporteRequest(
        String empresaId,
        String metrica,        // ver MetricaService.consulta() para el catálogo soportado
        String desde,          // yyyy-MM-dd (opcional)
        String hasta,          // yyyy-MM-dd (opcional)
        String estado,         // ACTIVA | COMPLETADA | CANCELADA | ERROR (opcional)
        String procesoId,      // filtra por proceso (opcional)
        String funcionarioId,  // filtra por funcionario (opcional)
        Integer limite,        // top N (opcional)
        String orden,          // "asc" | "desc" (opcional, default desc)
        String titulo          // título legible para el reporte (opcional)
) {}
