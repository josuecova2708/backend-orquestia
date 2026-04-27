package com.orquestia.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "usuarios")
public class Usuario {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String password;

    private String nombre;
    private String apellido;

    @Builder.Default
    private Rol rol = Rol.FUNCIONARIO;

    private String empresaId;
    private String departamentoId;

    @Builder.Default
    private List<String> empresasAdmin = new ArrayList<>();

    @Builder.Default
    private List<String> deviceTokens = new ArrayList<>();

    @Builder.Default
    private boolean activo = true;

    @CreatedDate
    private Instant fechaCreacion;
}
