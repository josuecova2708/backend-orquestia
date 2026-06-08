package com.orquestia.demo;

import com.orquestia.auth.Rol;
import com.orquestia.auth.Usuario;
import com.orquestia.auth.UsuarioRepository;
import com.orquestia.empresa.Departamento;
import com.orquestia.empresa.DepartamentoRepository;
import com.orquestia.empresa.Empresa;
import com.orquestia.empresa.EmpresaRepository;
import com.orquestia.instancia.InstanciaProceso;
import com.orquestia.instancia.InstanciaRepository;
import com.orquestia.instancia.TareaInstancia;
import com.orquestia.instancia.TareaRepository;
import com.orquestia.proceso.CampoFormulario;
import com.orquestia.proceso.Conexion;
import com.orquestia.proceso.Nodo;
import com.orquestia.proceso.Proceso;
import com.orquestia.proceso.ProcesoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Siembra una empresa "Demo Deep Learning" completa y autónoma, pensada para
 * demostrar el módulo de predicción (Fase 5) sin tocar las empresas reales.
 *
 * Construye los documentos DIRECTAMENTE vía repositorios (no por el motor BPM)
 * porque necesitamos controlar fechaCreacion, fechaCompletado, intentos y la
 * carga por funcionario — cosa que el motor (que fija todo a now()) no permite.
 * Los documentos resultantes son idénticos en forma a los que produce el motor.
 *
 * Genera:
 *   - 5 departamentos + 8 funcionarios (password: demo1234)
 *   - 2 procesos realistas PUBLICADOS (Crédito Personal, Atención de Reclamo)
 *   - ~60 instancias COMPLETADAS históricas (timeline backdateado) → da
 *     duración histórica promedio por nodo
 *   - ~15 instancias ACTIVAS con tareas pendientes calibradas en riesgo
 *     bajo / medio / alto (horario, carga del funcionario, reintentos)
 *
 * Es idempotente: si ya existe la empresa demo, borra todos sus datos y la
 * recrea desde cero.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoSeedService {

    public static final String EMPRESA_NOMBRE = "Demo Deep Learning";
    private static final String PASSWORD_DEMO = "demo1234";
    private static final String EMAIL_DOMAIN = "demodl.com";

    private final EmpresaRepository empresaRepository;
    private final DepartamentoRepository departamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProcesoRepository procesoRepository;
    private final InstanciaRepository instanciaRepository;
    private final TareaRepository tareaRepository;
    private final PasswordEncoder passwordEncoder;

    /** Duración histórica promedio (minutos) por etiqueta de nodo. Define qué nodos son "lentos". */
    private static final Map<String, Integer> DURACION_MEDIA = Map.of(
            "Recepción de solicitud", 20,
            "Análisis de riesgo crediticio", 180,
            "Elaboración de contrato", 120,
            "Desembolso", 45,
            "Registro del reclamo", 15,
            "Diagnóstico técnico", 90,
            "Escalamiento a Operaciones", 240,
            "Verificación de calidad", 30
    );

    private final Random rnd = new Random(42);

    // =========================================================================
    //  PUNTO DE ENTRADA
    // =========================================================================

    public Map<String, Object> sembrar(String adminUserId) {
        Usuario admin = usuarioRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + adminUserId));

        limpiarDemoPrevia(adminUserId);

        // 1. Empresa
        Empresa empresa = empresaRepository.save(Empresa.builder()
                .nombre(EMPRESA_NOMBRE)
                .descripcion("Empresa de demostración para el módulo de predicción con Deep Learning.")
                .rubro("Servicios Financieros")
                .creadoPor(adminUserId)
                .activa(true)
                .build());
        String empresaId = empresa.getId();

        // 2. Departamentos
        Map<String, String> dep = new LinkedHashMap<>(); // nombre → departamentoId
        for (String nombre : List.of("Atención al Cliente", "Análisis de Riesgo",
                "Legal", "Operaciones", "Soporte Técnico")) {
            Departamento d = departamentoRepository.save(Departamento.builder()
                    .nombre(nombre)
                    .descripcion("Departamento de " + nombre)
                    .empresaId(empresaId)
                    .activo(true)
                    .build());
            dep.put(nombre, d.getId());
        }

        // 3. Funcionarios (nombre, apellido, departamento)
        String[][] personas = {
                {"Ana", "García", "Atención al Cliente"},
                {"Luis", "Fernández", "Atención al Cliente"},
                {"Carla", "Rojas", "Análisis de Riesgo"},
                {"Pedro", "Suárez", "Legal"},
                {"María", "López", "Operaciones"},
                {"Jorge", "Vaca", "Operaciones"},
                {"Sofía", "Méndez", "Soporte Técnico"},
                {"Diego", "Torres", "Soporte Técnico"},
        };
        Map<String, String> userPorDepto = new HashMap<>(); // departamento → userId (primer funcionario del depto)
        List<Map<String, String>> credenciales = new ArrayList<>();
        for (String[] p : personas) {
            String email = slug(p[0]) + "." + slug(p[1]) + "@" + EMAIL_DOMAIN;
            Usuario u = usuarioRepository.save(Usuario.builder()
                    .email(email)
                    .password(passwordEncoder.encode(PASSWORD_DEMO))
                    .nombre(p[0])
                    .apellido(p[1])
                    .rol(Rol.FUNCIONARIO)
                    .empresaId(empresaId)
                    .departamentoId(dep.get(p[2]))
                    .activo(true)
                    .build());
            userPorDepto.putIfAbsent(p[2], u.getId());
            credenciales.add(Map.of("nombre", p[0] + " " + p[1], "email", email, "departamento", p[2]));
        }

        // 4. Procesos publicados
        Proceso credito = crearProcesoCredito(empresaId, adminUserId, dep, userPorDepto);
        Proceso reclamo = crearProcesoReclamo(empresaId, adminUserId, dep, userPorDepto);

        // 5. Datos: histórico (completadas) + activas calibradas en riesgo
        int historicas = 0;
        historicas += sembrarHistoricas(credito, SEQ_CREDITO, 30);
        historicas += sembrarHistoricas(reclamo, SEQ_RECLAMO, 30);

        int activas = sembrarActivas(credito, reclamo, userPorDepto);

        // 6. Vincular empresa demo al admin para que pueda seleccionarla en la UI
        if (admin.getEmpresasAdmin() == null) admin.setEmpresasAdmin(new ArrayList<>());
        if (!admin.getEmpresasAdmin().contains(empresaId)) {
            admin.getEmpresasAdmin().add(empresaId);
            usuarioRepository.save(admin);
        }

        log.info("Seed demo completado: empresa={}, funcionarios={}, históricas={}, activas={}",
                empresaId, personas.length, historicas, activas);

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("empresaId", empresaId);
        resumen.put("empresaNombre", EMPRESA_NOMBRE);
        resumen.put("departamentos", dep.size());
        resumen.put("funcionarios", personas.length);
        resumen.put("procesos", 2);
        resumen.put("instanciasHistoricas", historicas);
        resumen.put("instanciasActivas", activas);
        resumen.put("passwordDemo", PASSWORD_DEMO);
        resumen.put("credenciales", credenciales);
        return resumen;
    }

    // =========================================================================
    //  LIMPIEZA (idempotencia)
    // =========================================================================

    private void limpiarDemoPrevia(String adminUserId) {
        List<Empresa> previas = empresaRepository.findByNombre(EMPRESA_NOMBRE);
        for (Empresa e : previas) {
            String eid = e.getId();

            // Tareas de todas las instancias de la empresa
            List<String> instanciaIds = instanciaRepository.findByEmpresaId(eid)
                    .stream().map(InstanciaProceso::getId).collect(Collectors.toList());
            if (!instanciaIds.isEmpty()) {
                tareaRepository.deleteAll(tareaRepository.findByInstanciaIdIn(instanciaIds));
                instanciaRepository.deleteAllById(instanciaIds);
            }

            procesoRepository.deleteAll(procesoRepository.findByEmpresaId(eid));

            // Solo funcionarios/clientes de la demo; nunca un ADMIN (protege al usuario real)
            List<Usuario> usuariosDemo = usuarioRepository.findByEmpresaId(eid).stream()
                    .filter(u -> u.getRol() != Rol.ADMIN)
                    .collect(Collectors.toList());
            usuarioRepository.deleteAll(usuariosDemo);

            departamentoRepository.deleteAll(departamentoRepository.findByEmpresaId(eid));
            empresaRepository.deleteById(eid);

            // Desvincular del admin
            usuarioRepository.findById(adminUserId).ifPresent(a -> {
                if (a.getEmpresasAdmin() != null && a.getEmpresasAdmin().remove(eid)) {
                    if (eid.equals(a.getEmpresaId())) a.setEmpresaId(null);
                    usuarioRepository.save(a);
                }
            });
            log.info("Demo previa eliminada: empresa {}", eid);
        }
    }

    // =========================================================================
    //  DEFINICIÓN DE PROCESOS
    // =========================================================================

    /** Secuencia de actividades del happy-path (ids de nodo) usada para sembrar instancias. */
    private static final List<String> SEQ_CREDITO = List.of("n2", "n3", "n5", "n6");
    private static final List<String> SEQ_RECLAMO = List.of("m2", "m3", "m5", "m6");

    private Proceso crearProcesoCredito(String empresaId, String adminUserId,
                                        Map<String, String> dep, Map<String, String> userPorDepto) {
        List<Nodo> nodos = List.of(
                nodo("n1", "INICIO", "Inicio", null, null),
                nodo("n2", "ACTIVIDAD", "Recepción de solicitud", dep.get("Atención al Cliente"),
                        List.of(campoNumero("monto", "Monto solicitado"))),
                nodo("n3", "ACTIVIDAD", "Análisis de riesgo crediticio", dep.get("Análisis de Riesgo"),
                        List.of(campoOpciones("decision", "Decisión", List.of("Aprobado", "Rechazado")),
                                campoNumero("puntaje", "Puntaje de buró"))),
                nodo("n4", "GATEWAY_XOR", "¿Aprobado?", null, null),
                nodo("n5", "ACTIVIDAD", "Elaboración de contrato", dep.get("Legal"),
                        List.of(campoBooleano("contrato_ok", "Contrato firmado"))),
                nodo("n6", "ACTIVIDAD", "Desembolso", dep.get("Operaciones"),
                        List.of(campoBooleano("desembolsado", "Fondos liberados"))),
                nodo("n7", "FIN", "Crédito otorgado", null, null),
                nodo("n8", "FIN", "Solicitud rechazada", null, null)
        );
        List<Conexion> conexiones = List.of(
                conexion("c1", "n1", "n2", "NORMAL", null, null, false),
                conexion("c2", "n2", "n3", "NORMAL", null, null, false),
                conexion("c3", "n3", "n4", "NORMAL", null, null, false),
                conexion("c4", "n4", "n5", "CONDICIONAL", "Aprobado", "#decision == 'Aprobado'", false),
                conexion("c5", "n4", "n8", "NORMAL", "Rechazado", null, true),
                conexion("c6", "n5", "n6", "NORMAL", null, null, false),
                conexion("c7", "n6", "n7", "NORMAL", null, null, false)
        );
        Map<String, String> asignaciones = asignacionesDe(dep, userPorDepto,
                "Atención al Cliente", "Análisis de Riesgo", "Legal", "Operaciones");

        return guardarProcesoPublicado("Solicitud de Crédito Personal",
                "Evaluación y otorgamiento de un crédito personal: recepción, análisis de riesgo, contrato y desembolso.",
                empresaId, adminUserId, nodos, conexiones, asignaciones);
    }

    private Proceso crearProcesoReclamo(String empresaId, String adminUserId,
                                        Map<String, String> dep, Map<String, String> userPorDepto) {
        List<Nodo> nodos = List.of(
                nodo("m1", "INICIO", "Inicio", null, null),
                nodo("m2", "ACTIVIDAD", "Registro del reclamo", dep.get("Atención al Cliente"),
                        List.of(campoOpciones("tipo", "Tipo de reclamo",
                                List.of("Facturación", "Servicio", "Producto")))),
                nodo("m3", "ACTIVIDAD", "Diagnóstico técnico", dep.get("Soporte Técnico"),
                        List.of(campoOpciones("decision", "Resultado", List.of("Resuelto", "Escalar")))),
                nodo("m4", "GATEWAY_XOR", "¿Resuelto?", null, null),
                nodo("m5", "ACTIVIDAD", "Escalamiento a Operaciones", dep.get("Operaciones"),
                        List.of(campoBooleano("resuelto", "Resuelto en operaciones"))),
                nodo("m6", "ACTIVIDAD", "Verificación de calidad", dep.get("Atención al Cliente"),
                        List.of(campoBooleano("conforme", "Cliente conforme"))),
                nodo("m7", "FIN", "Reclamo cerrado", null, null)
        );
        List<Conexion> conexiones = List.of(
                conexion("d1", "m1", "m2", "NORMAL", null, null, false),
                conexion("d2", "m2", "m3", "NORMAL", null, null, false),
                conexion("d3", "m3", "m4", "NORMAL", null, null, false),
                conexion("d4", "m4", "m6", "CONDICIONAL", "Resuelto", "#decision == 'Resuelto'", false),
                conexion("d5", "m4", "m5", "NORMAL", "Escalar", null, true),
                conexion("d6", "m5", "m6", "NORMAL", null, null, false),
                conexion("d7", "m6", "m7", "NORMAL", null, null, false)
        );
        Map<String, String> asignaciones = asignacionesDe(dep, userPorDepto,
                "Atención al Cliente", "Soporte Técnico", "Operaciones");

        return guardarProcesoPublicado("Atención de Reclamo",
                "Gestión de un reclamo de cliente: registro, diagnóstico, posible escalamiento y verificación de calidad.",
                empresaId, adminUserId, nodos, conexiones, asignaciones);
    }

    private Proceso guardarProcesoPublicado(String nombre, String descripcion, String empresaId,
                                            String adminUserId, List<Nodo> nodos,
                                            List<Conexion> conexiones, Map<String, String> asignaciones) {
        return procesoRepository.save(Proceso.builder()
                .nombre(nombre)
                .descripcion(descripcion)
                .empresaId(empresaId)
                .creadoPor(adminUserId)
                .estado("PUBLICADO")
                .nodos(new ArrayList<>(nodos))
                .conexiones(new ArrayList<>(conexiones))
                .asignaciones(asignaciones)
                .version(2)
                .build());
    }

    // =========================================================================
    //  SIEMBRA DE INSTANCIAS
    // =========================================================================

    /** Crea {cantidad} instancias COMPLETADAS con timeline backdateado (últimos 60 días). */
    private int sembrarHistoricas(Proceso proceso, List<String> seq, int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            LocalDateTime inicio = LocalDateTime.now()
                    .minusDays(2 + rnd.nextInt(58))
                    .withHour(8 + rnd.nextInt(9))
                    .withMinute(rnd.nextInt(60));

            InstanciaProceso inst = nuevaInstancia(proceso, "COMPLETADA", inicio);
            LocalDateTime cursor = inicio;
            for (String nodoId : seq) {
                Nodo nodo = findNodo(proceso, nodoId);
                int dur = duracionDe(nodo.getLabel());
                LocalDateTime creada = cursor;
                LocalDateTime completada = creada.plusMinutes(dur);
                tareaRepository.save(tarea(inst.getId(), nodo, asignado(proceso, nodo),
                        "COMPLETADA", creada, completada, 0));
                cursor = completada.plusMinutes(5 + rnd.nextInt(25)); // gap entre actividades
            }
            inst.setFechaFin(cursor);
            instanciaRepository.save(inst);
        }
        return cantidad;
    }

    /**
     * Crea instancias ACTIVAS con una tarea PENDIENTE calibrada en riesgo.
     * El riesgo emerge de: nodo lento (histórico), horario de creación,
     * reintentos y carga del funcionario (cuántas pendientes acumula).
     */
    private int sembrarActivas(Proceso credito, Proceso reclamo, Map<String, String> userPorDepto) {
        int total = 0;
        // CRÉDITO ─────────────────────────────────────────────────────────────
        // Cluster ALTO riesgo: 5 pendientes en "Análisis de riesgo" (nodo lento) →
        // sobrecarga al analista + horario nocturno + reintentos
        for (int i = 0; i < 5; i++) {
            total += activa(credito, SEQ_CREDITO, 1, finDeSemanaMadrugada(i), 1 + (i % 2));
        }
        // BAJO riesgo: 2 pendientes en "Recepción" (nodo rápido), día hábil, sin reintentos
        total += activa(credito, SEQ_CREDITO, 0, diaHabilMañana(0), 0);
        total += activa(credito, SEQ_CREDITO, 0, diaHabilMañana(1), 0);
        // MEDIO: 2 pendientes en "Elaboración de contrato", tarde
        total += activa(credito, SEQ_CREDITO, 2, ayerTarde(0), 0);
        total += activa(credito, SEQ_CREDITO, 2, ayerTarde(1), 1);

        // RECLAMO ─────────────────────────────────────────────────────────────
        // ALTO/MEDIO: 3 pendientes en "Escalamiento a Operaciones" (nodo muy lento)
        for (int i = 0; i < 3; i++) {
            total += activa(reclamo, SEQ_RECLAMO, 2, i == 0 ? finDeSemanaMadrugada(5 + i) : ayerTarde(2 + i), 1 + (i % 2));
        }
        // BAJO: 2 pendientes en "Registro del reclamo"
        total += activa(reclamo, SEQ_RECLAMO, 0, diaHabilMañana(2), 0);
        total += activa(reclamo, SEQ_RECLAMO, 0, diaHabilMañana(3), 0);
        // MEDIO: 1 pendiente en "Diagnóstico técnico"
        total += activa(reclamo, SEQ_RECLAMO, 1, ayerTarde(5), 1);

        return total;
    }

    /**
     * Crea UNA instancia activa: completa las actividades previas (backdateadas)
     * y deja PENDIENTE la actividad en el índice {stopIndex} de la secuencia.
     */
    private int activa(Proceso proceso, List<String> seq, int stopIndex,
                       LocalDateTime pendienteCreada, int intentos) {
        // La instancia arrancó un poco antes de la tarea pendiente
        LocalDateTime inicio = pendienteCreada.minusHours(2L + stopIndex * 3L);
        InstanciaProceso inst = nuevaInstancia(proceso, "ACTIVA", inicio);

        LocalDateTime cursor = inicio;
        for (int i = 0; i < stopIndex; i++) {
            Nodo nodo = findNodo(proceso, seq.get(i));
            int dur = duracionDe(nodo.getLabel());
            tareaRepository.save(tarea(inst.getId(), nodo, asignado(proceso, nodo),
                    "COMPLETADA", cursor, cursor.plusMinutes(dur), 0));
            cursor = cursor.plusMinutes(dur + 5L);
        }
        // Tarea pendiente (la que el modelo evaluará)
        Nodo pendiente = findNodo(proceso, seq.get(stopIndex));
        tareaRepository.save(tarea(inst.getId(), pendiente, asignado(proceso, pendiente),
                "PENDIENTE", pendienteCreada, null, intentos));
        return 1;
    }

    // =========================================================================
    //  HELPERS DE CONSTRUCCIÓN
    // =========================================================================

    private InstanciaProceso nuevaInstancia(Proceso proceso, String estado, LocalDateTime inicio) {
        return instanciaRepository.save(InstanciaProceso.builder()
                .procesoId(proceso.getId())
                .procesoNombre(proceso.getNombre())
                .empresaId(proceso.getEmpresaId())
                .creadoPor(proceso.getCreadoPor())
                .creadoPorNombre("Seed Demo")
                .estado(estado)
                .fechaInicio(inicio)
                .variables(new HashMap<>())
                .build());
    }

    private TareaInstancia tarea(String instanciaId, Nodo nodo, String asignadoA, String estado,
                                 LocalDateTime creada, LocalDateTime completada, int intentos) {
        return TareaInstancia.builder()
                .instanciaId(instanciaId)
                .nodoId(nodo.getId())
                .nodoLabel(nodo.getLabel())
                .departamentoId(nodo.getDepartamentoId())
                .asignadoA(asignadoA)
                .estado(estado)
                .intentos(intentos)
                .formularioCampos(nodo.getFormulario())
                .fechaCreacion(creada)
                .fechaCompletado(completada)
                .datos(new HashMap<>())
                .build();
    }

    private int duracionDe(String label) {
        int media = DURACION_MEDIA.getOrDefault(label, 60);
        double ruido = rnd.nextGaussian() * 0.25 * media;
        return Math.max(2, (int) Math.round(media + ruido));
    }

    private String asignado(Proceso proceso, Nodo nodo) {
        if (nodo.getDepartamentoId() == null) return null;
        return proceso.getAsignaciones().get(nodo.getDepartamentoId());
    }

    private Map<String, String> asignacionesDe(Map<String, String> dep, Map<String, String> userPorDepto,
                                               String... departamentos) {
        Map<String, String> a = new HashMap<>();
        for (String d : departamentos) {
            a.put(dep.get(d), userPorDepto.get(d));
        }
        return a;
    }

    private Nodo findNodo(Proceso proceso, String nodoId) {
        return proceso.getNodos().stream().filter(n -> n.getId().equals(nodoId)).findFirst()
                .orElseThrow(() -> new RuntimeException("Nodo no encontrado: " + nodoId));
    }

    // ── Timestamps calibrados ────────────────────────────────────────────────

    /** Madrugada del último domingo (riesgo: fuera de horario). i desplaza minutos. */
    private LocalDateTime finDeSemanaMadrugada(int i) {
        LocalDateTime d = LocalDateTime.now();
        while (d.getDayOfWeek() != DayOfWeek.SUNDAY) d = d.minusDays(1);
        return d.withHour(2).withMinute((i * 7) % 60).withSecond(0).withNano(0);
    }

    /** Martes por la mañana (riesgo bajo: horario laboral). */
    private LocalDateTime diaHabilMañana(int i) {
        LocalDateTime d = LocalDateTime.now().minusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.minusDays(1);
        return d.withHour(10).withMinute((i * 11) % 60).withSecond(0).withNano(0);
    }

    /** Ayer por la tarde (riesgo medio). */
    private LocalDateTime ayerTarde(int i) {
        return LocalDateTime.now().minusDays(1)
                .with(LocalTime.of(15 + (i % 3), (i * 13) % 60));
    }

    // ── Builders de nodo / conexión / campo ──────────────────────────────────

    private Nodo nodo(String id, String tipo, String label, String departamentoId, List<CampoFormulario> form) {
        return Nodo.builder()
                .id(id).tipo(tipo).label(label)
                .departamentoId(departamentoId)
                .responsableCliente(false)
                .formulario(form != null ? form : new ArrayList<>())
                .posX(0.0).posY(0.0)
                .build();
    }

    private Conexion conexion(String id, String origen, String destino, String tipo,
                              String label, String condicion, boolean esDefault) {
        return Conexion.builder()
                .id(id).origenId(origen).destinoId(destino).tipo(tipo)
                .label(label).condicion(condicion).esDefault(esDefault)
                .build();
    }

    private CampoFormulario campoNumero(String nombre, String label) {
        return CampoFormulario.builder().nombre(nombre).tipo("NUMERO").label(label).requerido(true).build();
    }

    private CampoFormulario campoBooleano(String nombre, String label) {
        return CampoFormulario.builder().nombre(nombre).tipo("BOOLEANO").label(label).requerido(true).build();
    }

    private CampoFormulario campoOpciones(String nombre, String label, List<String> opciones) {
        return CampoFormulario.builder().nombre(nombre).tipo("OPCIONES").label(label)
                .requerido(true).opciones(opciones).build();
    }

    /** Normaliza un texto a minúsculas sin acentos ni espacios (para emails). */
    private String slug(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
