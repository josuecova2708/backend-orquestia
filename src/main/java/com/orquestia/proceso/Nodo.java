package com.orquestia.proceso;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

    private String id;             // ID único dentro del proceso (ej: "node_abc123")
    private String tipo;           // INICIO, FIN, ACTIVIDAD, GATEWAY_XOR, GATEWAY_AND
    private String label;          // Texto visible en el diagrama
    private String descripcion;

    // Solo para tipo ACTIVIDAD: departamento asignado
    private String departamentoId;

    /**
     * Si true, esta ACTIVIDAD la realiza el CLIENTE dueño del trámite (autoservicio),
     * no un funcionario. El motor asigna la tarea al clienteId de la instancia y aparece
     * en el portal del cliente. Útil para pasos que solo surgen a mitad del flujo
     * (aceptar condiciones, firmar, agendar, pagar un arancel calculado, etc.).
     */
    private boolean responsableCliente;

    /**
     * Formulario dinámico embebido en la actividad.
     * Reemplaza el campo formularioId anterior.
     * Los datos que llena el usuario se convierten en variables de la instancia
     * y son evaluables con SpEL en los gateways XOR siguientes.
     */
    private List<CampoFormulario> formulario;

    // Posición visual en el canvas (para el diagramador)
    private Double posX;
    private Double posY;
}
