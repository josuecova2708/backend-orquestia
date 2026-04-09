package com.orquestia.proceso;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa un nodo en el diagrama de actividad.
 * 
 * Tipos de nodos:
 *   INICIO      → Punto de entrada del proceso
 *   FIN         → Punto de finalización
 *   ACTIVIDAD   → Tarea humana (un funcionario llena un formulario)
 *   GATEWAY_XOR → Decisión (solo UNA rama se activa) — Flujo Condicional/Opcional
 *   GATEWAY_AND → Paralelismo (TODAS las ramas se activan) — Flujo Simultáneo
 * 
 * Este es un subdocumento embebido dentro de Proceso (no es colección separada).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Nodo {

    private String id;             // ID único dentro del proceso (ej: "nodo_1", "nodo_2")
    private String tipo;           // INICIO, FIN, ACTIVIDAD, GATEWAY_XOR, GATEWAY_AND
    private String label;          // Texto visible en el diagrama
    private String descripcion;

    // Solo para tipo ACTIVIDAD:
    private String departamentoId;   // Qué departamento ejecuta esta actividad
    private String formularioId;     // Qué formulario debe llenar el funcionario

    // Posición visual en el canvas (para el diagramador)
    private Double posX;
    private Double posY;
}
