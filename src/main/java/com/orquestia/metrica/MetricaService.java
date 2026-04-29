package com.orquestia.metrica;

import com.orquestia.instancia.InstanciaProceso;
import com.orquestia.instancia.InstanciaRepository;
import com.orquestia.instancia.TareaInstancia;
import com.orquestia.instancia.TareaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetricaService {

    private final InstanciaRepository instanciaRepository;
    private final TareaRepository tareaRepository;

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
