package com.orquestia.auth.dto;

import com.orquestia.auth.Rol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
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
    private List<EmpresaResumen> empresasAdmin;
}
