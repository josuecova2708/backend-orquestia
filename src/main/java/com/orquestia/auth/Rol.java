package com.orquestia.auth;

/**
 * Roles del sistema Orquestia.
 * 
 * - ADMIN: Administrador de la empresa. Puede crear procesos, usuarios, departamentos.
 * - DISEÑADOR: Diseña los diagramas de actividad / workflows.
 * - FUNCIONARIO: Ejecuta tareas asignadas en los formularios.
 */
public enum Rol {
    ADMIN,
    DISEÑADOR,
    FUNCIONARIO
}
