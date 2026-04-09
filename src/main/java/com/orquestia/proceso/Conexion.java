package com.orquestia.proceso;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa una conexión (flecha) entre dos nodos del diagrama.
 * 
 * Tipos de conexión:
 *   NORMAL   → Secuencial, va de A → B siempre
 *   CONDICIONAL → Solo se activa si se cumple la condición (para GATEWAY_XOR)
 *   RETORNO  → Vuelve a un nodo anterior (Flujo Relativo/Loop)
 * 
 * También es subdocumento embebido dentro de Proceso.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conexion {

    private String id;
    private String origenId;       // ID del nodo de origen
    private String destinoId;      // ID del nodo de destino
    private String tipo;           // NORMAL, CONDICIONAL, RETORNO
    private String label;          // Texto sobre la flecha (ej: "Aprobado", "Rechazado")

    // Solo para tipo CONDICIONAL:
    private String condicion;      // Expresión a evaluar (ej: "datos.aprobado == true")

    // Solo para tipo RETORNO:
    private Integer maxReintentos; // Máximo de vueltas permitidas (evita loops infinitos)

    @Builder.Default
    private boolean esDefault = false; // Si es la rama por defecto de un gateway XOR
}
