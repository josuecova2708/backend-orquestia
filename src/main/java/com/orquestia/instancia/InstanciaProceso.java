package com.orquestia.instancia;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Representa UNA ejecución concreta de un proceso publicado.
 *
 * Ciclo de vida:
 *   ACTIVA → il proceso está siendo ejecutado por los departamentos
 *   COMPLETADA → llegó a un nodo FIN satisfactoriamente
 *   CANCELADA → fue interrumpida manualmente
 *   ERROR → el motor encontró un estado inválido (nodo sin salida, etc.)
 *
 * Las 'variables' acumulan todos los datos ingresados en los formularios
 * de cada tarea. Son la fuente de verdad para evaluar condiciones SpEL
 * en los gateways XOR.
 *
 * @Version activa Optimistic Locking de Spring Data:
 *   Si dos ramas AND terminan al mismo tiempo y ambas quieren actualizar
 *   'variables', Spring detectará el conflicto y reintentará automáticamente,
 *   nunca perdiendo datos de ninguna rama.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "instancias_proceso")
public class InstanciaProceso {

    @Id
    private String id;

    private String procesoId;       // ID del proceso (definición) que se ejecuta
    private String empresaId;
    private String creadoPor;       // userId que arrancó el proceso

    @Builder.Default
    private String estado = "ACTIVA"; // ACTIVA | COMPLETADA | CANCELADA | ERROR

    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    @Builder.Default
    private LocalDateTime fechaInicio = LocalDateTime.now();
    private LocalDateTime fechaFin;

    @Version
    private Long version;           // Control de concurrencia optimista
}
