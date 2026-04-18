package com.orquestia.proceso;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Define un campo dentro del formulario dinámico de una Actividad.
 *
 * Tipos:
 *   TEXTO    → input de texto libre
 *   NUMERO   → input numérico
 *   BOOLEANO → checkbox Sí/No
 *   OPCIONES → select con opciones predefinidas (ej: "Aprobado", "Rechazado")
 *   FECHA    → date picker
 *
 * Los valores que el funcionario ingresa se mapean a instancia.variables con
 * la clave 'nombre', permitiendo que SpEL los evalúe en los gateways XOR.
 * Ejemplo: campo {nombre: "decision"} → evaluado como #decision == 'Aprobado'
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampoFormulario {

    private String nombre;       // Clave de la variable (sin espacios, ej: "puntaje_buro")
    private String tipo;         // TEXTO | NUMERO | BOOLEANO | OPCIONES | FECHA
    private String label;        // Texto visible para el usuario
    private boolean requerido;   // Si es obligatorio antes de poder completar la tarea

    // Solo para tipo OPCIONES: la lista de valores posibles
    private List<String> opciones;
}
