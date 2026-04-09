package com.orquestia.empresa;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface EmpresaRepository extends MongoRepository<Empresa, String> {

    List<Empresa> findByActiva(boolean activa);

    List<Empresa> findByCreadoPor(String userId);
}
