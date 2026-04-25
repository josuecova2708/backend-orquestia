package com.orquestia.metrica;

import com.orquestia.instancia.InstanciaRepository;
import com.orquestia.instancia.TareaInstancia;
import com.orquestia.instancia.TareaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetricaService {

    private final InstanciaRepository instanciaRepository;
    private final TareaRepository tareaRepository;

    public MetricasEmpresaResponse calcular(String empresaId) {
        var instancias = instanciaRepository.findByEmpresaId(empresaId);

        // 1. Instancias por estado
        Map<String, Long> porEstado = instancias.stream()
                .collect(Collectors.groupingBy(i -> i.getEstado(), Collectors.counting()));

        if (instancias.isEmpty()) {
            return new MetricasEmpresaResponse(porEstado, List.of(), List.of(), List.of());
        }

        var ids = instancias.stream().map(i -> i.getId()).toList();
        var tareas = tareaRepository.findByInstanciaIdIn(ids);

        // 2. Cuello de botella: nodos más lentos (tareas completadas con tiempo medible)
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
                            .mapToLong(t -> java.time.Duration.between(t.getFechaCreacion(), t.getFechaCompletado()).toMinutes())
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

        // 4. Actividad reciente: instancias iniciadas en los últimos 30 días
        var hoy = LocalDate.now();
        var hace30 = hoy.minusDays(29);
        var fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        Map<String, Long> porDia = instancias.stream()
                .filter(i -> i.getFechaInicio() != null)
                .filter(i -> !i.getFechaInicio().toLocalDate().isBefore(hace30))
                .collect(Collectors.groupingBy(i -> i.getFechaInicio().toLocalDate().format(fmt), Collectors.counting()));

        List<MetricasEmpresaResponse.ActividadDia> actividad = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String fecha = hace30.plusDays(i).format(fmt);
            actividad.add(new MetricasEmpresaResponse.ActividadDia(fecha, porDia.getOrDefault(fecha, 0L)));
        }

        return new MetricasEmpresaResponse(porEstado, cuellos, carga, actividad);
    }
}
