package com.orquestia.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO = Data Transfer Object
 * Es lo que el frontend envía al backend para hacer login.
 * Los DTOs protegen el modelo: el frontend nunca ve ni envía el modelo directamente.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "El email es requerido")
    @Email(message = "Email inválido")
    private String email;

    @NotBlank(message = "La contraseña es requerida")
    private String password;
}
