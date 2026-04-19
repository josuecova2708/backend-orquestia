package com.orquestia.auth.dto;

import com.orquestia.auth.Rol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Respuesta que recibe el frontend después de login/register.
 * Incluye el JWT token que debe enviarse en cada petición subsecuente.
 */
@Data
@Builder
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String userId;
    private String email;
    private String nombre;
    private String apellido;
    private Rol rol;
    private String empresaId;
    private String departamentoId;
}
