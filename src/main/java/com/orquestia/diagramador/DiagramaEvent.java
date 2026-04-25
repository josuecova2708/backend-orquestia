package com.orquestia.diagramador;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagramaEvent {
    private String tipo;      // NODE_MOVED, NODE_ADDED, NODE_DELETED, CONEXION_ADDED, CONEXION_DELETED, CURSOR_MOVED
    private String userId;
    private String userName;
    private Map<String, Object> data;
}
