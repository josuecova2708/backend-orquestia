package com.orquestia.proceso;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Documento que el cliente debe aportar antes de iniciar una instancia.
 * Clase embebida en Proceso.documentosRequeridos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequisitoDocumento {

    private String nombre;               // ej. "DNI", "Comprobante de ingresos"
    private String descripcion;          // instrucción para el cliente
    private List<String> mimeTypesPermitidos; // ej. ["image/jpeg","application/pdf"]
    private boolean obligatorio;         // si es false el cliente puede omitirlo
}
