package com.orquestia.motor;

import com.orquestia.auth.UsuarioRepository;
import com.orquestia.instancia.InstanciaProceso;
import com.orquestia.instancia.InstanciaRepository;
import com.orquestia.instancia.TareaInstancia;
import com.orquestia.instancia.TareaRepository;
import com.orquestia.notificacion.NotificacionService;
import com.orquestia.proceso.Conexion;
import com.orquestia.proceso.Nodo;
import com.orquestia.proceso.Proceso;
import com.orquestia.proceso.ProcesoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final UsuarioRepository usuarioRepository;
    private final SimpMessagingTemplate ws;
    private final NotificacionService notificacionService;

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
        String creadoPorNombre = usuarioRepository.findById(usuarioId)
                .map(u -> u.getNombre() + " " + u.getApellido())
                .orElse(usuarioId);

        InstanciaProceso instancia = InstanciaProceso.builder()
                .procesoId(procesoId)
                .procesoNombre(proceso.getNombre())
                .empresaId(proceso.getEmpresaId())
                .creadoPor(usuarioId)
                .creadoPorNombre(creadoPorNombre)
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
            notificarEmpresa(instancia);
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
        // El contador de reintentos vive en las variables de la instancia,
        // con clave única por conexión RETORNO. Así funciona aunque el nodo
        // origen sea un GATEWAY_XOR (que nunca genera tareas propias).
        String keyIntentos = "__retorno_" + conexion.getId();
        int intentosActuales = ((Number) instancia.getVariables()
                .getOrDefault(keyIntentos, 0)).intValue();

        int maxReintentos = conexion.getMaxReintentos() != null ? conexion.getMaxReintentos() : 2;

        log.info("RETORNO evaluando conexión {}: {}/{} intentos usados",
                conexion.getId(), intentosActuales, maxReintentos);

        if (intentosActuales < maxReintentos) {
            instancia.getVariables().put(keyIntentos, intentosActuales + 1);
            instanciaRepository.save(instancia);
            crearTarea(instancia, nodoDestino, proceso.getAsignaciones());
            log.info("RETORNO activado: intento {}/{} → nodo {}",
                    intentosActuales + 1, maxReintentos, nodoDestino.getId());
        } else {
            log.warn("RETORNO agotado (máximo {} intentos). Buscando ruta de salida desde nodo {}",
                    maxReintentos, conexion.getOrigenId());

            List<Conexion> alternativas = proceso.getConexiones().stream()
                    .filter(c -> c.getOrigenId().equals(conexion.getOrigenId())
                              && !"RETORNO".equals(c.getTipo()))
                    .collect(Collectors.toList());

            if (alternativas.isEmpty()) {
                instancia.setEstado("ERROR");
                instanciaRepository.save(instancia);
                notificarEmpresa(instancia);
                log.error("Sin ruta de salida tras agotar reintentos en nodo {}. Instancia {} → ERROR",
                        conexion.getOrigenId(), instancia.getId());
            } else {
                // Preferir la conexión marcada esDefault; si ninguna lo está, usar la primera
                Conexion elegida = alternativas.stream()
                        .filter(Conexion::isEsDefault)
                        .findFirst()
                        .orElse(alternativas.get(0));
                Nodo nodoSalida = findNodo(proceso, elegida.getDestinoId());
                crearTarea(instancia, nodoSalida, proceso.getAsignaciones());
                log.info("Ruta de salida tomada (esDefault={}) → nodo {}", elegida.isEsDefault(), nodoSalida.getId());
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
        if ("GATEWAY_XOR".equals(nodo.getTipo()) || "FIN".equals(nodo.getTipo())) {
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

        TareaInstancia guardada = tareaRepository.save(tarea);

        if (asignadoA != null) {
            Map<String, Object> payload = Map.of(
                "tipo", "TAREA_ASIGNADA",
                "tareaId", guardada.getId(),
                "nodoLabel", nodo.getLabel(),
                "instanciaId", instancia.getId()
            );
            ws.convertAndSend("/topic/usuario/" + asignadoA, payload);
            notificacionService.crear(asignadoA, "TAREA_ASIGNADA",
                "Nueva tarea asignada: " + nodo.getLabel(),
                payload
            );
        }

        return guardada;
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
     * Devuelve las tareas PENDIENTES/EN_PROGRESO asignadas al usuario.
     * Esta es la "bandeja de entrada" del funcionario.
     */
    public List<TareaInstancia> obtenerMisTareas(String userId) {
        return tareaRepository.findByAsignadoAAndEstadoIn(userId, Arrays.asList("PENDIENTE", "EN_PROGRESO"));
    }

    /**
     * Devuelve todas las instancias en las que el usuario tuvo al menos una tarea,
     * ordenadas de más reciente a más antigua. Usado para el historial del funcionario.
     */
    public List<InstanciaProceso> obtenerMisInstancias(String userId) {
        List<String> instanciaIds = tareaRepository.findByAsignadoA(userId)
                .stream()
                .map(TareaInstancia::getInstanciaId)
                .distinct()
                .collect(Collectors.toList());
        List<InstanciaProceso> instancias = instanciaRepository.findAllById(instanciaIds)
                .stream()
                .sorted((a, b) -> b.getFechaInicio().compareTo(a.getFechaInicio()))
                .collect(Collectors.toList());
        enrichNombres(instancias);
        return instancias;
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
        notificarEmpresa(instancia);

        // Rechazar todas las tareas abiertas de esta instancia
        tareaRepository.findByInstanciaId(instanciaId).stream()
                .filter(t -> "PENDIENTE".equals(t.getEstado()) || "EN_PROGRESO".equals(t.getEstado()))
                .forEach(t -> {
                    t.setEstado("RECHAZADA");
                    tareaRepository.save(t);
                });

        log.info("Instancia {} cancelada y tareas rechazadas", instanciaId);
    }

    /** Lista instancias de una empresa. Sin estado → todas; con estado → filtradas. */
    public List<InstanciaProceso> listarInstancias(String empresaId, String estado) {
        List<InstanciaProceso> todas = instanciaRepository.findByEmpresaId(empresaId);
        if (estado != null && !estado.isBlank()) {
            todas = todas.stream().filter(i -> estado.equals(i.getEstado())).collect(Collectors.toList());
        }
        enrichNombres(todas);
        return todas;
    }

    /** Enriquece instancias antiguas con procesoNombre y creadoPorNombre si les falta. */
    private void enrichNombres(List<InstanciaProceso> instancias) {
        Set<String> procesoIds = instancias.stream()
                .filter(i -> i.getProcesoNombre() == null || i.getProcesoNombre().isBlank())
                .map(InstanciaProceso::getProcesoId)
                .collect(Collectors.toSet());
        Map<String, String> procesoNombres = new HashMap<>();
        procesoIds.forEach(pid -> procesoRepository.findById(pid)
                .ifPresent(p -> procesoNombres.put(pid, p.getNombre())));

        Set<String> userIds = instancias.stream()
                .filter(i -> i.getCreadoPorNombre() == null || i.getCreadoPorNombre().isBlank())
                .map(InstanciaProceso::getCreadoPor)
                .collect(Collectors.toSet());
        Map<String, String> userNombres = new HashMap<>();
        userIds.forEach(uid -> usuarioRepository.findById(uid)
                .ifPresent(u -> userNombres.put(uid, u.getNombre() + " " + u.getApellido())));

        instancias.forEach(i -> {
            if (i.getProcesoNombre() == null || i.getProcesoNombre().isBlank()) {
                i.setProcesoNombre(procesoNombres.getOrDefault(i.getProcesoId(), i.getProcesoId()));
            }
            if (i.getCreadoPorNombre() == null || i.getCreadoPorNombre().isBlank()) {
                i.setCreadoPorNombre(userNombres.getOrDefault(i.getCreadoPor(), i.getCreadoPor()));
            }
        });
    }

    /** @deprecated usar listarInstancias(empresaId, "ACTIVA") */
    public List<InstanciaProceso> listarInstanciasActivas(String empresaId) {
        return listarInstancias(empresaId, "ACTIVA");
    }

    // Notifica al canal de empresa cuando una instancia cambia de estado
    private void notificarEmpresa(InstanciaProceso instancia) {
        ws.convertAndSend("/topic/empresa/" + instancia.getEmpresaId(), Map.of(
                "tipo", "INSTANCIA_ACTUALIZADA",
                "instanciaId", instancia.getId(),
                "estado", instancia.getEstado()
        ));
    }
}
