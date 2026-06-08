package com.orquestia.metrica;

import com.orquestia.auth.Usuario;
import com.orquestia.auth.UsuarioRepository;
import com.orquestia.instancia.InstanciaProceso;
import com.orquestia.instancia.InstanciaRepository;
import com.orquestia.instancia.TareaInstancia;
import com.orquestia.instancia.TareaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetricaService {

    private final InstanciaRepository instanciaRepository;
    private final TareaRepository tareaRepository;
    private final UsuarioRepository usuarioRepository;

    public MetricasEmpresaResponse calcular(String empresaId, String desde, String hasta) {
        LocalDate startDate = desde != null ? LocalDate.parse(desde) : null;
        LocalDate endDate   = hasta != null ? LocalDate.parse(hasta) : null;

        var todasInstancias = instanciaRepository.findByEmpresaId(empresaId);

        // Apply date filter on fechaInicio if range is provided
        var instancias = (startDate == null && endDate == null) ? todasInstancias
                : todasInstancias.stream()
                    .filter(i -> {
                        if (i.getFechaInicio() == null) return false;
                        LocalDate f = i.getFechaInicio().toLocalDate();
                        if (startDate != null && f.isBefore(startDate)) return false;
                        if (endDate   != null && f.isAfter(endDate))    return false;
                        return true;
                    }).toList();

        // 1. Instancias por estado
        Map<String, Long> porEstado = instancias.stream()
                .collect(Collectors.groupingBy(InstanciaProceso::getEstado, Collectors.counting()));

        // Build activity series even when instancias is empty (shows empty chart)
        List<MetricasEmpresaResponse.ActividadDia> actividad = buildActividad(instancias, startDate, endDate);

        if (instancias.isEmpty()) {
            return new MetricasEmpresaResponse(porEstado, List.of(), List.of(), actividad, List.of());
        }

        var ids = instancias.stream().map(InstanciaProceso::getId).toList();
        var tareas = tareaRepository.findByInstanciaIdIn(ids);

        // 2. Cuello de botella: nodos más lentos (tareas completadas)
        List<MetricasEmpresaResponse.CuelloBotella> cuellos = tareas.stream()
                .filter(t -> "COMPLETADA".equals(t.getEstado())
                        && t.getFechaCreacion() != null
                        && t.getFechaCompletado() != null
                        && t.getNodoLabel() != null)
                .collect(Collectors.groupingBy(TareaInstancia::getNodoLabel))
                .entrySet().stream()
                .map(e -> {
                    var lista = e.getValue();
                    double avg = lista.stream()
                            .mapToLong(t -> Duration.between(t.getFechaCreacion(), t.getFechaCompletado()).toMinutes())
                            .average().orElse(0);
                    return new MetricasEmpresaResponse.CuelloBotella(e.getKey(), Math.round(avg * 10.0) / 10.0, lista.size());
                })
                .sorted(Comparator.comparingDouble(MetricasEmpresaResponse.CuelloBotella::avgMinutos).reversed())
                .limit(8)
                .toList();

        // 3. Carga actual por funcionario (tareas pendientes/en progreso)
        List<MetricasEmpresaResponse.CargaFuncionario> carga = tareas.stream()
                .filter(t -> ("PENDIENTE".equals(t.getEstado()) || "EN_PROGRESO".equals(t.getEstado()))
                        && t.getAsignadoA() != null)
                .collect(Collectors.groupingBy(TareaInstancia::getAsignadoA, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new MetricasEmpresaResponse.CargaFuncionario(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(MetricasEmpresaResponse.CargaFuncionario::pendientes).reversed())
                .limit(10)
                .toList();

        // 4. Tiempos por proceso: promedio de duración total de instancias COMPLETADAS
        List<MetricasEmpresaResponse.TiempoProceso> tiempos = instancias.stream()
                .filter(i -> "COMPLETADA".equals(i.getEstado())
                        && i.getFechaInicio() != null
                        && i.getFechaFin() != null
                        && i.getProcesoNombre() != null)
                .collect(Collectors.groupingBy(InstanciaProceso::getProcesoNombre))
                .entrySet().stream()
                .map(e -> {
                    var lista = e.getValue();
                    double avg = lista.stream()
                            .mapToLong(i -> Duration.between(i.getFechaInicio(), i.getFechaFin()).toMinutes())
                            .average().orElse(0);
                    return new MetricasEmpresaResponse.TiempoProceso(e.getKey(), Math.round(avg * 10.0) / 10.0, lista.size());
                })
                .sorted(Comparator.comparingDouble(MetricasEmpresaResponse.TiempoProceso::avgMinutos).reversed())
                .limit(8)
                .toList();

        return new MetricasEmpresaResponse(porEstado, cuellos, carga, actividad, tiempos);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Reportes personalizados por consulta (catálogo fijo + filtros flexibles)
    // ════════════════════════════════════════════════════════════════════════

    public ConsultaReporteResponse consulta(ConsultaReporteRequest req) {
        LocalDate startDate = (req.desde() != null && !req.desde().isBlank()) ? LocalDate.parse(req.desde()) : null;
        LocalDate endDate   = (req.hasta() != null && !req.hasta().isBlank()) ? LocalDate.parse(req.hasta()) : null;
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end   = endDate   != null ? endDate.atTime(LocalTime.MAX) : null;

        boolean orderAsc = "asc".equalsIgnoreCase(req.orden());
        Integer limite = (req.limite() != null && req.limite() > 0) ? req.limite() : null;

        // Universo de instancias de la empresa (con filtros de proceso/estado/fecha de inicio)
        var todas = instanciaRepository.findByEmpresaId(req.empresaId());
        Predicate<InstanciaProceso> filtroProceso = i ->
                req.procesoId() == null || req.procesoId().isBlank() || req.procesoId().equals(i.getProcesoId());
        Predicate<InstanciaProceso> filtroEstado = i ->
                req.estado() == null || req.estado().isBlank() || req.estado().equalsIgnoreCase(i.getEstado());
        Predicate<InstanciaProceso> filtroInicioEnRango = i -> {
            if (start == null && end == null) return true;
            LocalDateTime f = i.getFechaInicio();
            if (f == null) return false;
            if (start != null && f.isBefore(start)) return false;
            if (end   != null && f.isAfter(end))    return false;
            return true;
        };

        var instanciasFiltradas = todas.stream().filter(filtroProceso).filter(filtroEstado).toList();
        // Mapa instanciaId → proceso, para join desde tareas
        Map<String, InstanciaProceso> instPorId = instanciasFiltradas.stream()
                .collect(Collectors.toMap(InstanciaProceso::getId, i -> i, (a, b) -> a));

        String metrica = req.metrica() == null ? "" : req.metrica();
        List<String> columnas;
        List<List<Object>> filas;

        switch (metrica) {
            case "actividades_completadas_por_funcionario" -> {
                columnas = List.of("Funcionario", "Actividades completadas");
                var tareas = tareasDeInstancias(instPorId.keySet());
                Map<String, Long> conteo = tareas.stream()
                        .filter(t -> "COMPLETADA".equals(t.getEstado()))
                        .filter(t -> t.getAsignadoA() != null)
                        .filter(t -> req.funcionarioId() == null || req.funcionarioId().isBlank()
                                || req.funcionarioId().equals(t.getAsignadoA()))
                        .filter(t -> enRangoFecha(t.getFechaCompletado(), start, end))
                        .collect(Collectors.groupingBy(TareaInstancia::getAsignadoA, Collectors.counting()));
                filas = rankingUsuarios(conteo, orderAsc, limite);
            }
            case "carga_pendiente_por_funcionario" -> {
                columnas = List.of("Funcionario", "Tareas pendientes");
                var tareas = tareasDeInstancias(instPorId.keySet());
                Map<String, Long> conteo = tareas.stream()
                        .filter(t -> "PENDIENTE".equals(t.getEstado()) || "EN_PROGRESO".equals(t.getEstado()))
                        .filter(t -> t.getAsignadoA() != null)
                        .filter(t -> req.funcionarioId() == null || req.funcionarioId().isBlank()
                                || req.funcionarioId().equals(t.getAsignadoA()))
                        .collect(Collectors.groupingBy(TareaInstancia::getAsignadoA, Collectors.counting()));
                filas = rankingUsuarios(conteo, orderAsc, limite);
            }
            case "cuellos_de_botella" -> {
                columnas = List.of("Actividad", "Tiempo promedio (min)", "Mediciones");
                var tareas = tareasDeInstancias(instPorId.keySet());
                filas = tareas.stream()
                        .filter(t -> "COMPLETADA".equals(t.getEstado())
                                && t.getFechaCreacion() != null && t.getFechaCompletado() != null
                                && t.getNodoLabel() != null)
                        .filter(t -> enRangoFecha(t.getFechaCompletado(), start, end))
                        .collect(Collectors.groupingBy(TareaInstancia::getNodoLabel))
                        .entrySet().stream()
                        .map(e -> {
                            double avg = e.getValue().stream()
                                    .mapToLong(t -> Duration.between(t.getFechaCreacion(), t.getFechaCompletado()).toMinutes())
                                    .average().orElse(0);
                            return List.<Object>of(e.getKey(), round1(avg), (long) e.getValue().size());
                        })
                        .sorted(comparadorNumerico(1, orderAsc))
                        .limit(limite != null ? limite : Long.MAX_VALUE)
                        .collect(Collectors.toList());
            }
            case "tiempos_por_proceso" -> {
                columnas = List.of("Proceso", "Duración promedio (min)", "Ejecuciones");
                filas = instanciasFiltradas.stream()
                        .filter(filtroInicioEnRango)
                        .filter(i -> "COMPLETADA".equals(i.getEstado())
                                && i.getFechaInicio() != null && i.getFechaFin() != null
                                && i.getProcesoNombre() != null)
                        .collect(Collectors.groupingBy(InstanciaProceso::getProcesoNombre))
                        .entrySet().stream()
                        .map(e -> {
                            double avg = e.getValue().stream()
                                    .mapToLong(i -> Duration.between(i.getFechaInicio(), i.getFechaFin()).toMinutes())
                                    .average().orElse(0);
                            return List.<Object>of(e.getKey(), round1(avg), (long) e.getValue().size());
                        })
                        .sorted(comparadorNumerico(1, orderAsc))
                        .limit(limite != null ? limite : Long.MAX_VALUE)
                        .collect(Collectors.toList());
            }
            case "ejecuciones_por_proceso" -> {
                columnas = List.of("Proceso", "Ejecuciones");
                Map<String, Long> conteo = instanciasFiltradas.stream()
                        .filter(filtroInicioEnRango)
                        .filter(i -> i.getProcesoNombre() != null)
                        .collect(Collectors.groupingBy(InstanciaProceso::getProcesoNombre, Collectors.counting()));
                filas = conteo.entrySet().stream()
                        .map(e -> List.<Object>of(e.getKey(), e.getValue()))
                        .sorted(comparadorNumerico(1, orderAsc))
                        .limit(limite != null ? limite : Long.MAX_VALUE)
                        .collect(Collectors.toList());
            }
            case "ejecuciones_por_estado" -> {
                columnas = List.of("Estado", "Cantidad");
                Map<String, Long> conteo = instanciasFiltradas.stream()
                        .filter(filtroInicioEnRango)
                        .collect(Collectors.groupingBy(InstanciaProceso::getEstado, Collectors.counting()));
                filas = conteo.entrySet().stream()
                        .map(e -> List.<Object>of(e.getKey(), e.getValue()))
                        .sorted(comparadorNumerico(1, orderAsc))
                        .collect(Collectors.toList());
            }
            case "actividad_diaria" -> {
                columnas = List.of("Fecha", "Ejecuciones iniciadas");
                var fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                Map<String, Long> porDia = instanciasFiltradas.stream()
                        .filter(filtroInicioEnRango)
                        .filter(i -> i.getFechaInicio() != null)
                        .collect(Collectors.groupingBy(i -> i.getFechaInicio().toLocalDate().format(fmt), Collectors.counting()));
                filas = porDia.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> List.<Object>of(e.getKey(), e.getValue()))
                        .collect(Collectors.toList());
            }
            default -> {
                columnas = List.of("Error");
                filas = List.of(List.<Object>of("Métrica no soportada: " + metrica));
            }
        }

        String titulo = (req.titulo() != null && !req.titulo().isBlank()) ? req.titulo() : tituloPorDefecto(metrica);
        return new ConsultaReporteResponse(titulo, metrica, columnas, filas, filas.size());
    }

    private List<TareaInstancia> tareasDeInstancias(Collection<String> instanciaIds) {
        if (instanciaIds.isEmpty()) return List.of();
        return tareaRepository.findByInstanciaIdIn(instanciaIds);
    }

    private boolean enRangoFecha(LocalDateTime f, LocalDateTime start, LocalDateTime end) {
        if (start == null && end == null) return true;
        if (f == null) return false;
        if (start != null && f.isBefore(start)) return false;
        if (end   != null && f.isAfter(end))    return false;
        return true;
    }

    /** Convierte un conteo userId→cantidad en filas [nombre, cantidad] ordenadas. */
    private List<List<Object>> rankingUsuarios(Map<String, Long> conteo, boolean asc, Integer limite) {
        Map<String, String> nombres = nombresUsuarios(conteo.keySet());
        return conteo.entrySet().stream()
                .sorted(asc ? Map.Entry.comparingByValue()
                            : Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limite != null ? limite : Long.MAX_VALUE)
                .map(e -> List.<Object>of(nombres.getOrDefault(e.getKey(), e.getKey()), e.getValue()))
                .collect(Collectors.toList());
    }

    private Map<String, String> nombresUsuarios(Collection<String> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<String, String> map = new HashMap<>();
        for (Usuario u : usuarioRepository.findAllById(ids)) {
            map.put(u.getId(), (u.getNombre() + " " + u.getApellido()).trim());
        }
        return map;
    }

    /** Comparador por una columna numérica (Long/Double) de las filas. */
    private Comparator<List<Object>> comparadorNumerico(int col, boolean asc) {
        Comparator<List<Object>> c = Comparator.comparingDouble(fila -> {
            Object v = fila.get(col);
            return v instanceof Number n ? n.doubleValue() : 0;
        });
        return asc ? c : c.reversed();
    }

    private double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    private String tituloPorDefecto(String metrica) {
        return switch (metrica) {
            case "actividades_completadas_por_funcionario" -> "Actividades completadas por funcionario";
            case "carga_pendiente_por_funcionario" -> "Carga pendiente por funcionario";
            case "cuellos_de_botella" -> "Cuellos de botella";
            case "tiempos_por_proceso" -> "Tiempos por proceso";
            case "ejecuciones_por_proceso" -> "Ejecuciones por proceso";
            case "ejecuciones_por_estado" -> "Ejecuciones por estado";
            case "actividad_diaria" -> "Actividad diaria";
            default -> "Reporte";
        };
    }

    private List<MetricasEmpresaResponse.ActividadDia> buildActividad(
            List<InstanciaProceso> instancias, LocalDate startDate, LocalDate endDate) {

        LocalDate desde = startDate != null ? startDate : LocalDate.now().minusDays(29);
        LocalDate hasta = endDate   != null ? endDate   : LocalDate.now();

        // Cap to 90 days to avoid huge response
        long span = ChronoUnit.DAYS.between(desde, hasta);
        if (span > 90) {
            desde = hasta.minusDays(90);
        }

        var fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, Long> porDia = instancias.stream()
                .filter(i -> i.getFechaInicio() != null)
                .collect(Collectors.groupingBy(
                        i -> i.getFechaInicio().toLocalDate().format(fmt),
                        Collectors.counting()));

        List<MetricasEmpresaResponse.ActividadDia> result = new ArrayList<>();
        long days = ChronoUnit.DAYS.between(desde, hasta) + 1;
        for (long i = 0; i < days; i++) {
            String fecha = desde.plusDays(i).format(fmt);
            result.add(new MetricasEmpresaResponse.ActividadDia(fecha, porDia.getOrDefault(fecha, 0L)));
        }
        return result;
    }
}
