package com.orquestia.proceso;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modelo MongoDB para un Proceso (definición de workflow).
 * 
 * Un proceso es un GRAFO DIRIGIDO compuesto de:
 *   - Nodos (actividades, gateways, inicio, fin)
 *   - Conexiones (flechas entre nodos)
 * 
 * Estados del proceso:
 *   BORRADOR   → Se está diseñando, no se puede ejecutar
 *   PUBLICADO  → Listo para crear instancias y ejecutar
 *   ARCHIVADO  → Ya no se usa pero se conserva el historial
 * 
 * Los nodos y conexiones se guardan EMBEBIDOS dentro del documento Proceso.
 * En MongoDB esto es eficiente porque todo el grafo se lee en una sola query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "procesos")
public class Proceso {

    @Id
    private String id;

    private String nombre;
    private String descripcion;
    private String empresaId;
    private String creadoPor;     // userId del diseñador

    @Builder.Default
    private String estado = "BORRADOR";  // BORRADOR, PUBLICADO, ARCHIVADO

    @Builder.Default
    private List<Nodo> nodos = new ArrayList<>();

    @Builder.Default
    private List<Conexion> conexiones = new ArrayList<>();

    /** departamentoId → userId: quién de ese depto ejecuta las tareas en ESTE proceso */
    @Builder.Default
    private Map<String, String> asignaciones = new HashMap<>();

    @Builder.Default
    private int version = 1;

    @CreatedDate
    private Instant fechaCreacion;

    @LastModifiedDate
    private Instant fechaModificacion;
}
