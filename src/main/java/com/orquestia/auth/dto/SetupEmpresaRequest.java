package com.orquestia.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request para el onboarding: cuando un usuario recién registrado crea su empresa.
 * Recibe el nombre y rubro de la empresa a crear.
 */
@Data
public class SetupEmpresaRequest {

    @NotBlank(message = "El nombre de la empresa es obligatorio")
    private String nombre;

    private String descripcion;

    @NotBlank(message = "El rubro es obligatorio")
    private String rubro;
}
