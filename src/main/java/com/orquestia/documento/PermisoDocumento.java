package com.orquestia.documento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermisoDocumento {
    private String usuarioId;
    private String usuarioNombre;
    private TipoPermiso tipo; // LECTURA, ESCRITURA, ADMIN

    public enum TipoPermiso { LECTURA, ESCRITURA, ADMIN }
}
