package com.orquestia.empresa;

import com.orquestia.empresa.dto.DepartamentoRequest;
import com.orquestia.empresa.dto.EmpresaRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST para gestión de Empresas y Departamentos.
 *
 * Todas las rutas requieren autenticación JWT (excepto las de /api/auth).
 * El userId se obtiene del SecurityContext (puesto ahí por JwtAuthenticationFilter).
 *
 * Endpoints:
 *   POST   /api/empresas               → Crear empresa
 *   GET    /api/empresas               → Listar empresas activas
 *   GET    /api/empresas/{id}          → Obtener empresa por ID
 *   PUT    /api/empresas/{id}          → Actualizar empresa
 *   DELETE /api/empresas/{id}          → Eliminar empresa (soft delete)
 *
 *   POST   /api/empresas/{empresaId}/departamentos       → Crear departamento
 *   GET    /api/empresas/{empresaId}/departamentos       → Listar departamentos
 *   PUT    /api/departamentos/{id}                       → Actualizar departamento
 *   DELETE /api/departamentos/{id}                       → Eliminar departamento
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EmpresaController {

    private final EmpresaService empresaService;

    // ==================== EMPRESAS ====================

    @PostMapping("/empresas")
    public ResponseEntity<Empresa> crearEmpresa(
            @Valid @RequestBody EmpresaRequest request,
            Authentication auth) {
        String userId = auth.getName(); // El "name" es el userId (lo pusimos en el JWT subject)
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(empresaService.crearEmpresa(request, userId));
    }

    @GetMapping("/empresas")
    public ResponseEntity<List<Empresa>> listarEmpresas() {
        return ResponseEntity.ok(empresaService.listarEmpresas());
    }

    @GetMapping("/empresas/{id}")
    public ResponseEntity<Empresa> obtenerEmpresa(@PathVariable String id) {
        return ResponseEntity.ok(empresaService.obtenerEmpresa(id));
    }

    @PutMapping("/empresas/{id}")
    public ResponseEntity<Empresa> actualizarEmpresa(
            @PathVariable String id,
            @Valid @RequestBody EmpresaRequest request) {
        return ResponseEntity.ok(empresaService.actualizarEmpresa(id, request));
    }

    @DeleteMapping("/empresas/{id}")
    public ResponseEntity<Void> eliminarEmpresa(@PathVariable String id) {
        empresaService.eliminarEmpresa(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== DEPARTAMENTOS ====================

    @PostMapping("/empresas/{empresaId}/departamentos")
    public ResponseEntity<Departamento> crearDepartamento(
            @PathVariable String empresaId,
            @Valid @RequestBody DepartamentoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(empresaService.crearDepartamento(empresaId, request));
    }

    @GetMapping("/empresas/{empresaId}/departamentos")
    public ResponseEntity<List<Departamento>> listarDepartamentos(@PathVariable String empresaId) {
        return ResponseEntity.ok(empresaService.listarDepartamentos(empresaId));
    }

    @PutMapping("/departamentos/{id}")
    public ResponseEntity<Departamento> actualizarDepartamento(
            @PathVariable String id,
            @Valid @RequestBody DepartamentoRequest request) {
        return ResponseEntity.ok(empresaService.actualizarDepartamento(id, request));
    }

    @DeleteMapping("/departamentos/{id}")
    public ResponseEntity<Void> eliminarDepartamento(@PathVariable String id) {
        empresaService.eliminarDepartamento(id);
        return ResponseEntity.noContent().build();
    }
}
