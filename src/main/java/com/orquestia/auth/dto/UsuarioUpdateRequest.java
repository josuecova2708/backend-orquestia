package com.orquestia.auth.dto;

import com.orquestia.auth.Rol;
import lombok.Data;

/**
 * DTO para actualizar datos de un usuario existente.
 * Todos los campos son opcionales — solo se actualizan los que se envían.
 */
@Data
public class UsuarioUpdateRequest {

    private String nombre;
    private String apellido;
    private Rol rol;
    private String empresaId;
    private String departamentoId;
    private Boolean activo;
}
