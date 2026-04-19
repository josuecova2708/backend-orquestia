package com.orquestia.auth;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository para acceder a la colección "usuarios" en MongoDB.
 * 
 * Spring Data MongoDB genera la implementación automáticamente.
 * Solo necesitas declarar el método con la convención de nombres:
 *   findByEmail → busca por campo "email"
 *   findByEmpresaId → busca por campo "empresaId"
 *   existsByEmail → retorna true/false si existe
 * 
 * MongoRepository<Usuario, String> → <Tipo de documento, Tipo del ID>
 */
public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Usuario> findByEmpresaId(String empresaId);

    List<Usuario> findByEmpresaIdAndDepartamentoId(String empresaId, String departamentoId);

    boolean existsByEmpresaIdAndDepartamentoIdAndRol(String empresaId, String departamentoId, Rol rol);
}
