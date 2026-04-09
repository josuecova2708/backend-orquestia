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

/**
 * Modelo MongoDB para usuarios del sistema.
 * 
 * @Document → Le dice a Spring que esto se guarda como documento en MongoDB (colección "usuarios")
 * @Data → Lombok genera getters, setters, toString, equals, hashCode automáticamente
 * @Builder → Permite crear objetos con: Usuario.builder().email("x").build()
 */
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

    private String password; // BCrypt hash, NUNCA texto plano

    private String nombre;
    private String apellido;

    @Builder.Default
    private Rol rol = Rol.FUNCIONARIO;

    private String empresaId;       // Referencia a la empresa
    private String departamentoId;  // Referencia al departamento (para funcionarios)

    @Builder.Default
    private boolean activo = true;

    @CreatedDate
    private Instant fechaCreacion;
}
