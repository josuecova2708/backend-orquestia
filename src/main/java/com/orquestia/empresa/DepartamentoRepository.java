package com.orquestia.empresa;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DepartamentoRepository extends MongoRepository<Departamento, String> {

    List<Departamento> findByEmpresaId(String empresaId);

    List<Departamento> findByEmpresaIdAndActivo(String empresaId, boolean activo);
}
