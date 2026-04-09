package com.orquestia.empresa.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmpresaRequest {

    @NotBlank(message = "El nombre es requerido")
    private String nombre;

    private String descripcion;

    private String rubro;
}
