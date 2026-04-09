package com.orquestia.auth.dto;

import com.orquestia.auth.Rol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * DTO de respuesta para listar usuarios.
 * NUNCA retornamos el password al frontend, por eso no usamos el modelo Usuario directo.
 */
@Data
@Builder
@AllArgsConstructor
public class UsuarioResponse {

    private String id;
    private String email;
    private String nombre;
    private String apellido;
    private Rol rol;
    private String empresaId;
    private String departamentoId;
    private boolean activo;
    private Instant fechaCreacion;
}
