package com.orquestia.proceso.dto;

import com.orquestia.proceso.Conexion;
import com.orquestia.proceso.Nodo;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * DTO para crear/actualizar un proceso.
 * Los nodos y conexiones pueden venir vacíos al crear (se agregarán en el diagramador).
 */
@Data
public class ProcesoRequest {

    @NotBlank(message = "El nombre es requerido")
    private String nombre;

    private String descripcion;

    @NotBlank(message = "La empresa es requerida")
    private String empresaId;

    // Estos se envían cuando el diseñador guarda el diagrama
    private List<Nodo> nodos;
    private List<Conexion> conexiones;
}
