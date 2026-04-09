package com.orquestia.proceso;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProcesoRepository extends MongoRepository<Proceso, String> {

    List<Proceso> findByEmpresaId(String empresaId);

    List<Proceso> findByEmpresaIdAndEstado(String empresaId, String estado);

    List<Proceso> findByCreadoPor(String userId);
}
