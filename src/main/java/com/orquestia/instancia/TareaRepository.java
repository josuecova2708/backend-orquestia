package com.orquestia.instancia;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface TareaRepository extends MongoRepository<TareaInstancia, String> {

    // Todas las tareas de una ejecución
    List<TareaInstancia> findByInstanciaId(String instanciaId);

    // Tareas de una ejecución para un nodo específico (usado por AND Join)
    List<TareaInstancia> findByInstanciaIdAndNodoId(String instanciaId, String nodoId);

    // Tareas pendientes de un departamento (vista del funcionario)
    List<TareaInstancia> findByDepartamentoIdAndEstado(String departamentoId, String estado);

    // Tareas de una ejecución en cierto estado (para verificar AND Join)
    List<TareaInstancia> findByInstanciaIdAndEstado(String instanciaId, String estado);

    // Para contar si una tarea de un nodo origen ya está completada (AND Join check)
    List<TareaInstancia> findByInstanciaIdAndNodoIdAndEstado(String instanciaId, String nodoId, String estado);
}
