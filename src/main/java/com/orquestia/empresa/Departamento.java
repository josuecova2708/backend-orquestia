package com.orquestia.empresa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Modelo MongoDB para departamentos dentro de una empresa.
 * 
 * Los departamentos son las unidades organizacionales donde se asignan
 * las actividades del workflow. Ejemplo: "Atención al Cliente", "Dpto. Técnico",
 * "Facturación", "Legal", "Almacén".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "departamentos")
public class Departamento {

    @Id
    private String id;

    private String nombre;
    private String descripcion;

    private String empresaId;   // Referencia a la empresa a la que pertenece

    @Builder.Default
    private boolean activo = true;

    @CreatedDate
    private Instant fechaCreacion;
}
