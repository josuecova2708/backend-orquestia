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
 * Modelo MongoDB para empresas.
 * 
 * Cada empresa puede tener múltiples departamentos, procesos y usuarios.
 * El sistema es multi-tenant: cada empresa tiene sus propios datos aislados.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "empresas")
public class Empresa {

    @Id
    private String id;

    private String nombre;
    private String descripcion;
    private String rubro;       // Ej: "Energía Eléctrica", "Banca", "Salud"

    private String creadoPor;   // userId del admin que la creó

    @Builder.Default
    private boolean activa = true;

    @CreatedDate
    private Instant fechaCreacion;
}
