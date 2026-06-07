package com.orquestia.documento;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface DocumentoRepository extends MongoRepository<Documento, String> {
    List<Documento> findByInstanciaIdOrderByFechaCreacionDesc(String instanciaId);
    List<Documento> findByEmpresaIdOrderByFechaCreacionDesc(String empresaId);
    List<Documento> findByEmpresaIdAndProcesoIdOrderByFechaCreacionDesc(String empresaId, String procesoId);
    List<Documento> findByEmpresaIdAndClienteIdOrderByFechaCreacionDesc(String empresaId, String clienteId);
    List<Documento> findByEmpresaIdAndTipoOrderByFechaCreacionDesc(String empresaId, Documento.TipoDocumento tipo);
    List<Documento> findByInstanciaIdAndTipo(String instanciaId, Documento.TipoDocumento tipo);
    List<Documento> findByInstanciaIdInOrderByFechaCreacionDesc(Collection<String> instanciaIds);
    List<Documento> findByClienteIdOrderByFechaCreacionDesc(String clienteId);
}
