package com.orquestia.instancia;

import com.orquestia.proceso.CampoFormulario;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representa una tarea concreta asignada a un departamento
 * dentro de una ejecución (InstanciaProceso).
 *
 * Ciclo de vida:
 *   PENDIENTE    → Creada por el motor, esperando ser tomada
 *   EN_PROGRESO  → Alguien del departamento la marcó como "Estoy en esto"
 *   COMPLETADA   → El formulario fue llenado y enviado → el motor avanza
 *   RECHAZADA    → Cancelada externamente (por ejemplo si la instancia se cancela)
 *
 * 'intentos' es el contador de loops. El motor lo compara contra
 * Conexion.maxReintentos para decidir si hacer retorno o forzar salida de error.
 *
 * 'datos' contiene el Map de los campos del formulario llenados por el usuario.
 * al completar, el motor hace: instancia.variables.putAll(tarea.datos)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tareas_instancia")
public class TareaInstancia {

    @Id
    private String id;

    @Indexed
    private String instanciaId;     // A qué ejecución pertenece

    private String nodoId;          // ID del nodo en la definición del proceso
    private String nodoLabel;       // Label del nodo (para mostrar en la UI sin joins)
    private String departamentoId;  // Qué departamento debe completarla
    private String asignadoA;       // userId específico (opcional; puede ser cualquiera del depto)

    @Builder.Default
    private String estado = "PENDIENTE"; // PENDIENTE | EN_PROGRESO | COMPLETADA | RECHAZADA

    @Builder.Default
    private int intentos = 0;       // Contador de veces que este nodo fue retornado (loops)

    @Builder.Default
    private Map<String, Object> datos = new HashMap<>(); // Datos del formulario

    private String comentario;

    /** Campos del formulario copiados del Nodo en el momento de creación de la tarea.
     *  Así el frontend puede renderizar el formulario sin hacer una query extra al proceso. */
    private List<CampoFormulario> formularioCampos;

    @Builder.Default
    private LocalDateTime fechaCreacion = LocalDateTime.now();
    private LocalDateTime fechaCompletado;
}
