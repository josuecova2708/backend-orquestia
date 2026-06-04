package com.orquestia.proceso.dto;

import com.orquestia.proceso.RequisitoDocumento;
import lombok.Data;

import java.util.List;

@Data
public class ConfiguracionClienteRequest {

    private boolean habilitadoParaClientes;
    private List<RequisitoDocumento> documentosRequeridos;
}
