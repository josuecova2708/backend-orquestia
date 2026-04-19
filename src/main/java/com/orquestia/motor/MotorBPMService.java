package com.orquestia.motor;

import com.orquestia.instancia.InstanciaProceso;
import com.orquestia.instancia.InstanciaRepository;
import com.orquestia.instancia.TareaInstancia;
import com.orquestia.instancia.TareaRepository;
import com.orquestia.proceso.Conexion;
import com.orquestia.proceso.Nodo;
import com.orquestia.proceso.Proceso;
import com.orquestia.proceso.ProcesoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * MotorBPMService — Corazón del sistema de ejecución de procesos.
 *
 * Responsabilidades:
 *   1. Iniciar una instancia desde un proceso publicado
 *   2. Completar una tarea y avanzar el flujo automáticamente
 *   3. Resolver los 4 tipos de navegación:
 *      - NORMAL (secuencial)
 *      - XOR (decisión con SpEL)
 *      - AND Fork (paralelo saliente)
 *      - AND Join (esperar que todas las ramas entren)
 *      - RETORNO (loop con límite de reintentos)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MotorBPMService {

    private final ProcesoRepository procesoRepository;
    private final InstanciaRepository instanciaRepository;
    private final TareaRepository tareaRepository;

    private final ExpressionParser spel = new SpelExpressionParser();

    // =========================================================================
    // MÉTODO 1: Iniciar proceso
    // =========================================================================

    /**
     * Crea una InstanciaProceso nueva y genera la primera tarea.
     *
     * @param procesoId ID del proceso PUBLICADO a ejecutar
     * @param usuarioId Quien arranca el proceso
     * @param variablesIniciales Variables iniciales opcionales (ej: datos del cliente)
     */
    public InstanciaProceso iniciarProceso(String procesoId, String usuarioId,
                                           Map<String, Object> variablesIniciales) {
        // 1. Cargar la definición del proceso
        Proceso proceso = procesoRepository.findById(procesoId)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado: " + procesoId));

        if (!"PUBLICADO".equals(proceso.getEstado())) {
            throw new RuntimeException("Solo se pueden ejecutar procesos en estado PUBLICADO");
        }

        // 2. Crear la instancia
        InstanciaProceso instancia = InstanciaProceso.builder()
                .procesoId(procesoId)
                .empresaId(proceso.getEmpresaId())
                .creadoPor(usuarioId)
                .build();

        if (variablesIniciales != null) {
            instancia.getVariables().putAll(variablesIniciales);
        }
        instancia = instanciaRepository.save(instancia);

        // 3. Encontrar el nodo INICIO y avanzar desde él
        Nodo nodoInicio = proceso.getNodos().stream()
                .filter(n -> "INICIO".equals(n.getTipo()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("El proceso no tiene nodo INICIO"));

        avanzar(instancia, nodoInicio, proceso);

        log.info("Instancia {} iniciada para proceso {}", instancia.getId(), procesoId);
        return instancia;
    }

    // =========================================================================
    // MÉTODO 2: Completar tarea
    // =========================================================================

    /**
     * El funcionario completó su tarea. El motor:
     *   1. Cierra la tarea
     *   2. Mezcla los datos del formulario en las variables de la instancia
     *   3. Avanza al siguiente nodo según el tipo (XOR, AND, NORMAL, etc.)
     *
     * @param tareaId   ID de la TareaInstancia a completar
     * @param datos     Datos del formulario llenado por el usuario
     * @param comentario Comentario opcional
     */
    public TareaInstancia completarTarea(String tareaId, Map<String, Object> datos, String comentario) {
        // 1. Cargar y validar la tarea
        TareaInstancia tarea = tareaRepository.findById(tareaId)
                .orElseThrow(() -> new RuntimeException("Tarea no encontrada: " + tareaId));

        if ("COMPLETADA".equals(tarea.getEstado())) {
            throw new RuntimeException("Esta tarea ya fue completada");
        }

        // 2. Cerrar la tarea
        tarea.setEstado("COMPLETADA");
        tarea.setFechaCompletado(LocalDateTime.now());
        tarea.setComentario(comentario);
        if (datos != null) tarea.setDatos(datos);
        tareaRepository.save(tarea);

        // 3. Actualizar variables de la instancia con los datos del formulario
        InstanciaProceso instancia = instanciaRepository.findById(tarea.getInstanciaId())
                .orElseThrow(() -> new RuntimeException("Instancia no encontrada"));

        if (datos != null) {
            instancia.getVariables().putAll(datos);
            instanciaRepository.save(instancia); // @Version protege de concurrencia
        }

        // 4. Cargar la definición del proceso y avanzar
        Proceso proceso = procesoRepository.findById(instancia.getProcesoId())
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado"));

        Nodo nodoActual = proceso.getNodos().stream()
                .filter(n -> n.getId().equals(tarea.getNodoId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Nodo no encontrado en la definición"));

        // Verificamos si hay una conexión de salida tipo RETORNO desde este nodo
        Conexion conexionRetorno = proceso.getConexiones().stream()
                .filter(c -> c.getOrigenId().equals(nodoActual.getId()) && "RETORNO".equals(c.getTipo()))
                .findFirst()
                .orElse(null);

        // Lógica MVP: Si la flecha existe y el campo decision dice "rechazado", tomamos el bucle
        boolean debeRetornar = false;
        if (conexionRetorno != null && datos != null && datos.containsKey("decision")) {
            String decisionValor = String.valueOf(datos.get("decision")).trim();
            if ("rechazado".equalsIgnoreCase(decisionValor)) {
                debeRetornar = true;
            }
        }

        if (debeRetornar) {
            log.info("Iniciando ruta de RETORNO manual desde la tarea {}", tarea.getId());
            Nodo destino = findNodo(proceso, conexionRetorno.getDestinoId());
            evaluarRetorno(instancia, proceso, conexionRetorno, destino);
        } else {
            avanzar(instancia, nodoActual, proceso);
        }

        log.info("Tarea {} completada en instancia {}", tareaId, instancia.getId());
        return tarea;
    }

    // =========================================================================
    // MÉTODO 3: Motor de navegación — avanzar()
    // =========================================================================

    /**
     * El núcleo del motor. Dado un nodo que acaba de completarse,
     * decide qué hacer a continuación según el tipo de nodo y sus conexiones.
     */
    private void avanzar(InstanciaProceso instancia, Nodo nodoActual, Proceso proceso) {
        String tipo = nodoActual.getTipo();

        // === FIN ===
        if ("FIN".equals(tipo)) {
            instancia.setEstado("COMPLETADA");
            instancia.setFechaFin(LocalDateTime.now());
            instanciaRepository.save(instancia);
            log.info("Instancia {} COMPLETADA", instancia.getId());
            return;
        }

        // Obtener conexiones salientes del nodo actual
        List<Conexion> salientes = proceso.getConexiones().stream()
                .filter(c -> c.getOrigenId().equals(nodoActual.getId()))
                .collect(Collectors.toList());

        if (salientes.isEmpty()) {
            log.warn("Nodo {} no tiene conexiones salientes en instancia {}", nodoActual.getId(), instancia.getId());
            return;
        }

        switch (tipo) {
            case "INICIO":
            case "ACTIVIDAD":
                resolverNormal(instancia, proceso, salientes);
                break;

            case "GATEWAY_XOR":
                resolverXOR(instancia, proceso, salientes);
                break;

            case "GATEWAY_AND":
                resolverANDFork(instancia, proceso, salientes);
                break;

            default:
                log.warn("Tipo de nodo desconocido: {}", tipo);
        }
    }

    // =========================================================================
    // CASO 1: NORMAL — secuencial
    // =========================================================================

    private void resolverNormal(InstanciaProceso instancia, Proceso proceso, List<Conexion> salientes) {
        for (Conexion conexion : salientes) {
            if ("RETORNO".equals(conexion.getTipo())) {
                // El retorno se evalúa con la tarea anterior, no con la conexión directamente
                // Esto se maneja en completarTarea vía evaluarRetorno()
                continue;
            }
            Nodo destino = findNodo(proceso, conexion.getDestinoId());
            crearTarea(instancia, destino, proceso.getAsignaciones());

            if ("GATEWAY_AND".equals(destino.getTipo())) {
                verificarANDJoin(instancia, proceso, destino);
            }
        }
    }

    // =========================================================================
    // CASO 2: XOR — decisión con SpEL
    // =========================================================================

    /**
     * Evalúa cada conexión saliente del gateway XOR contra las variables de la instancia.
     * Solo avanza por la PRIMERA que cumpla la condición. Si ninguna cumple,
     * busca la conexión marcada como esDefault.
     */
    private void resolverXOR(InstanciaProceso instancia, Proceso proceso, List<Conexion> salientes) {
        log.info("Evaluando XOR {} con variables {}", salientes.get(0).getOrigenId(), instancia.getVariables());

        // 1. Primero intentar con condiciones SpEL explícitas
        for (Conexion conexion : salientes) {
            if (conexion.getCondicion() != null && !conexion.getCondicion().isBlank()) {
                boolean cumple = evaluarCondicion(conexion.getCondicion(), instancia.getVariables());
                log.info("Condición SpEL '{}' evaluada a {}", conexion.getCondicion(), cumple);
                if (cumple) {
                    avanzarPorXOR(instancia, proceso, conexion);
                    return; // XOR: solo UNA ruta
                }
            }
        }

        // 2. Si hay un campo "decision" en las variables, probar si coincide exactamente con el label de la conexión
        if (instancia.getVariables().containsKey("decision")) {
            String decisionValor = String.valueOf(instancia.getVariables().get("decision")).trim();
            for (Conexion conexion : salientes) {
                if (conexion.getLabel() != null && conexion.getLabel().trim().equalsIgnoreCase(decisionValor)) {
                    log.info("Ruta XOR elegida por coincidencia de label: '{}' == '{}'", conexion.getLabel(), decisionValor);
                    avanzarPorXOR(instancia, proceso, conexion);
                    return;
                }
            }
        }

        // 3. Fallback: usar la conexión por defecto si existe
        for (Conexion conexion : salientes) {
            if (conexion.isEsDefault()) {
                log.info("Ruta XOR elegida por defecto (esDefault = true)");
                avanzarPorXOR(instancia, proceso, conexion);
                return;
            }
        }

        log.error("El XOR {} se atascó. Ninguna condición se cumplió, los labels no coinciden, y no hay ruta default.", salientes.get(0).getOrigenId());
        // Podríamos poner la instancia en ERROR, pero la dejamos ACTIVA por ahora.
    }

    private void avanzarPorXOR(InstanciaProceso instancia, Proceso proceso, Conexion conexion) {
        Nodo destino = findNodo(proceso, conexion.getDestinoId());
        if ("RETORNO".equals(conexion.getTipo())) {
            evaluarRetorno(instancia, proceso, conexion, destino);
        } else {
            crearTarea(instancia, destino, proceso.getAsignaciones());
            if ("GATEWAY_AND".equals(destino.getTipo())) {
                verificarANDJoin(instancia, proceso, destino);
            }
        }
    }

    // =========================================================================
    // CASO 3: AND Fork — crear todas las tareas paralelas
    // =========================================================================

    private void resolverANDFork(InstanciaProceso instancia, Proceso proceso, List<Conexion> salientes) {
        for (Conexion conexion : salientes) {
            Nodo destino = findNodo(proceso, conexion.getDestinoId());
            crearTarea(instancia, destino, proceso.getAsignaciones());
            log.info("Rama paralela creada → nodo {} en instancia {}", destino.getId(), instancia.getId());
        }
    }

    // =========================================================================
    // CASO 4: AND Join — esperar que todas las ramas entren
    // =========================================================================

    /**
     * Verifica si TODAS las ramas que llegan al nodo JOIN ya están completadas.
     * Si es así, avanza al siguiente nodo. Si no, espera.
     *
     * ALGORITMO CORRECTO: cuenta aristas entrantes desde la DEFINICIÓN del proceso,
     * no desde las tareas activas. Esto evita el race condition de "una rama termina
     * antes de que la otra cree su tarea".
     */
    private void verificarANDJoin(InstanciaProceso instancia, Proceso proceso, Nodo nodoJoin) {
        // 1. Cuántas conexiones entran al nodo JOIN según el diagrama
        List<Conexion> entrantes = proceso.getConexiones().stream()
                .filter(c -> c.getDestinoId().equals(nodoJoin.getId()))
                .collect(Collectors.toList());

        int totalRamas = entrantes.size();

        if (totalRamas <= 1) {
            // Es un AND Fork puro, no tiene nada que sincronizar. Avanza directo.
            avanzar(instancia, nodoJoin, proceso);
            return;
        }

        // 2. Para cada nodo origen, verificar que su tarea en esta instancia esté COMPLETADA
        long ramasCompletadas = entrantes.stream()
                .filter(c -> {
                    List<TareaInstancia> tareasDelOrigen = tareaRepository
                            .findByInstanciaIdAndNodoIdAndEstado(instancia.getId(), c.getOrigenId(), "COMPLETADA");
                    return !tareasDelOrigen.isEmpty();
                })
                .count();

        log.info("AND Join nodo {}: {}/{} ramas completadas", nodoJoin.getId(), ramasCompletadas, totalRamas);

        if (ramasCompletadas == totalRamas) {
            // ¡Todas las ramas terminaron! Avanzar más allá del JOIN
            log.info("AND Join satisfecho → avanzando desde nodo {}", nodoJoin.getId());
            avanzar(instancia, nodoJoin, proceso);
        }
        // Si no, simplemente no hacer nada — la otra rama eventualmente completará y llamará esto de nuevo
    }

    // =========================================================================
    // CASO 5: RETORNO — loop con límite de reintentos
    // =========================================================================

    /**
     * Decide si una ruta RETORNO se ejecuta o se fuerza la salida de error.
     *
     * @param conexion La conexión RETORNO que se está intentando activar
     * @param nodoDestino El nodo al que volvería (nodo anterior en el grafo)
     */
    private void evaluarRetorno(InstanciaProceso instancia, Proceso proceso,
                                 Conexion conexion, Nodo nodoDestino) {
        // Buscar la tarea actual del nodo origen del retorno para leer sus intentos
        String nodoOrigenId = conexion.getOrigenId();
        List<TareaInstancia> tareasOrigen = tareaRepository
                .findByInstanciaIdAndNodoId(instancia.getId(), nodoOrigenId);

        int intentosActuales = tareasOrigen.stream()
                .mapToInt(TareaInstancia::getIntentos)
                .max().orElse(0);

        int maxReintentos = conexion.getMaxReintentos() != null ? conexion.getMaxReintentos() : 2;

        if (intentosActuales < maxReintentos) {
            TareaInstancia nuevaTarea = crearTarea(instancia, nodoDestino, proceso.getAsignaciones());
            nuevaTarea.setIntentos(intentosActuales + 1);
            tareaRepository.save(nuevaTarea);
            log.info("RETORNO activado: intento {}/{} hacia nodo {}", intentosActuales + 1, maxReintentos, nodoDestino.getId());
        } else {
            // ❌ Límite superado: forzar salida de error (buscar conexión alternativa)
            log.warn("RETORNO agotado ({} intentos). Forzando ruta de error desde nodo {}", intentosActuales, nodoOrigenId);

            List<Conexion> alternativas = proceso.getConexiones().stream()
                    .filter(c -> c.getOrigenId().equals(nodoOrigenId) && !"RETORNO".equals(c.getTipo()))
                    .collect(Collectors.toList());

            if (alternativas.isEmpty()) {
                instancia.setEstado("ERROR");
                instanciaRepository.save(instancia);
                log.error("Sin ruta de salida de error en nodo {}. Instancia {} en ERROR", nodoOrigenId, instancia.getId());
            } else {
                Nodo nodoError = findNodo(proceso, alternativas.get(0).getDestinoId());
                crearTarea(instancia, nodoError, proceso.getAsignaciones());
            }
        }
    }

    // =========================================================================
    // UTILIDADES
    // =========================================================================

    /**
     * Crea una TareaInstancia pendiente para el nodo dado.
     * El departamento se toma del nodo si es ACTIVIDAD; gateways no crean tarea real.
     */
    private TareaInstancia crearTarea(InstanciaProceso instancia, Nodo nodo, Map<String, String> asignaciones) {
        if ("GATEWAY_XOR".equals(nodo.getTipo())) {
            Proceso proceso = procesoRepository.findById(instancia.getProcesoId()).orElseThrow();
            avanzar(instancia, nodo, proceso);
            return null;
        }
        if ("GATEWAY_AND".equals(nodo.getTipo())) {
            return null;
        }

        String asignadoA = (asignaciones != null && nodo.getDepartamentoId() != null)
                ? asignaciones.get(nodo.getDepartamentoId())
                : null;

        TareaInstancia tarea = TareaInstancia.builder()
                .instanciaId(instancia.getId())
                .nodoId(nodo.getId())
                .nodoLabel(nodo.getLabel())
                .departamentoId(nodo.getDepartamentoId())
                .asignadoA(asignadoA)
                .formularioCampos(nodo.getFormulario())
                .build();

        return tareaRepository.save(tarea);
    }

    /**
     * Evalúa una expresión SpEL contra el mapa de variables de la instancia.
     * Ejemplo: condicion = "#decision == 'Aprobado'"
     *          variables = {decision: "Aprobado"} → true
     */
    private boolean evaluarCondicion(String condicion, Map<String, Object> variables) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariables(variables);
            Boolean resultado = spel.parseExpression(condicion).getValue(context, Boolean.class);
            return Boolean.TRUE.equals(resultado);
        } catch (Exception e) {
            log.warn("Error evaluando condición '{}': {}", condicion, e.getMessage());
            return false;
        }
    }

    /**
     * Busca un nodo en la definición del proceso por su ID.
     */
    private Nodo findNodo(Proceso proceso, String nodoId) {
        return proceso.getNodos().stream()
                .filter(n -> n.getId().equals(nodoId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Nodo no encontrado: " + nodoId));
    }

    // =========================================================================
    // CONSULTAS PARA EL FRONTEND
    // =========================================================================

    public InstanciaProceso obtenerInstancia(String id) {
        return instanciaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Instancia no encontrada: " + id));
    }

    public List<TareaInstancia> obtenerTareasDeInstancia(String instanciaId) {
        return tareaRepository.findByInstanciaId(instanciaId);
    }

    /**
     * Devuelve las tareas PENDIENTES del departamento del usuario logueado.
     * Esta es la "bandeja de entrada" del funcionario.
     */
    public List<TareaInstancia> obtenerMisTareas(String userId) {
        return tareaRepository.findByAsignadoAAndEstadoIn(userId, Arrays.asList("PENDIENTE", "EN_PROGRESO"));
    }

    public TareaInstancia iniciarTrabajo(String tareaId) {
        TareaInstancia tarea = tareaRepository.findById(tareaId)
                .orElseThrow(() -> new RuntimeException("Tarea no encontrada: " + tareaId));
        tarea.setEstado("EN_PROGRESO");
        return tareaRepository.save(tarea);
    }

    /**
     * Cancela una instancia y rechaza todas sus tareas pendientes/en progreso.
     * Útil para limpiar ejecuciones de prueba.
     */
    public void cancelarInstancia(String instanciaId) {
        InstanciaProceso instancia = instanciaRepository.findById(instanciaId)
                .orElseThrow(() -> new RuntimeException("Instancia no encontrada: " + instanciaId));
        instancia.setEstado("CANCELADA");
        instancia.setFechaFin(LocalDateTime.now());
        instanciaRepository.save(instancia);

        // Rechazar todas las tareas abiertas de esta instancia
        tareaRepository.findByInstanciaId(instanciaId).stream()
                .filter(t -> "PENDIENTE".equals(t.getEstado()) || "EN_PROGRESO".equals(t.getEstado()))
                .forEach(t -> {
                    t.setEstado("RECHAZADA");
                    tareaRepository.save(t);
                });

        log.info("Instancia {} cancelada y tareas rechazadas", instanciaId);
    }

    /** Lista las instancias activas de una empresa para el panel de control. */
    public List<InstanciaProceso> listarInstanciasActivas(String empresaId) {
        return instanciaRepository.findByEmpresaId(empresaId).stream()
                .filter(i -> "ACTIVA".equals(i.getEstado()))
                .collect(Collectors.toList());
    }
}
