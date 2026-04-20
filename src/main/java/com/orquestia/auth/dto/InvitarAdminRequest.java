package com.orquestia.auth.dto;

import lombok.Data;

@Data
public class InvitarAdminRequest {
    private String email;
    private String nombre;
    private String apellido;
    private String password;
    private String empresaId;
}
