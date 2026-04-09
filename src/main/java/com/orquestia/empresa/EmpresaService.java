package com.orquestia.empresa;

import com.orquestia.empresa.dto.DepartamentoRequest;
import com.orquestia.empresa.dto.EmpresaRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio de negocio para Empresas y Departamentos.
 *
 * Patrón: Controller recibe HTTP → llama al Service → Service usa Repository → Repository habla con MongoDB
 * El Service contiene la LÓGICA DE NEGOCIO: validaciones, reglas, transformaciones.
 */
@Service
@RequiredArgsConstructor
public class EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final DepartamentoRepository departamentoRepository;

    // ==================== EMPRESAS ====================

    public Empresa crearEmpresa(EmpresaRequest request, String userId) {
        Empresa empresa = Empresa.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .rubro(request.getRubro())
                .creadoPor(userId)
                .build();
        return empresaRepository.save(empresa);
    }

    public List<Empresa> listarEmpresas() {
        return empresaRepository.findByActiva(true);
    }

    public Empresa obtenerEmpresa(String id) {
        return empresaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Empresa no encontrada con id: " + id));
    }

    public Empresa actualizarEmpresa(String id, EmpresaRequest request) {
        Empresa empresa = obtenerEmpresa(id);
        empresa.setNombre(request.getNombre());
        empresa.setDescripcion(request.getDescripcion());
        empresa.setRubro(request.getRubro());
        return empresaRepository.save(empresa);
    }

    public void eliminarEmpresa(String id) {
        Empresa empresa = obtenerEmpresa(id);
        empresa.setActiva(false); // Soft delete — no borramos, desactivamos
        empresaRepository.save(empresa);
    }

    // ==================== DEPARTAMENTOS ====================

    public Departamento crearDepartamento(String empresaId, DepartamentoRequest request) {
        // Verificar que la empresa existe
        obtenerEmpresa(empresaId);

        Departamento departamento = Departamento.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .empresaId(empresaId)
                .build();
        return departamentoRepository.save(departamento);
    }

    public List<Departamento> listarDepartamentos(String empresaId) {
        return departamentoRepository.findByEmpresaIdAndActivo(empresaId, true);
    }

    public Departamento obtenerDepartamento(String id) {
        return departamentoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Departamento no encontrado con id: " + id));
    }

    public Departamento actualizarDepartamento(String id, DepartamentoRequest request) {
        Departamento departamento = obtenerDepartamento(id);
        departamento.setNombre(request.getNombre());
        departamento.setDescripcion(request.getDescripcion());
        return departamentoRepository.save(departamento);
    }

    public void eliminarDepartamento(String id) {
        Departamento departamento = obtenerDepartamento(id);
        departamento.setActivo(false);
        departamentoRepository.save(departamento);
    }
}
