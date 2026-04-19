package com.orquestia.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CrearFuncionarioRequest {

    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 6)
    private String password;

    @NotBlank
    private String nombre;

    @NotBlank
    private String apellido;

    private String departamentoId;
}
