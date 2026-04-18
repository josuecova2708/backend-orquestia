package com.orquestia.instancia;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface InstanciaRepository extends MongoRepository<InstanciaProceso, String> {
    List<InstanciaProceso> findByEmpresaId(String empresaId);
    List<InstanciaProceso> findByProcesoIdAndEstado(String procesoId, String estado);
}
