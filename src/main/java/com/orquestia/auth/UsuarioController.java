package com.orquestia.auth;

import com.orquestia.auth.dto.UsuarioResponse;
import com.orquestia.auth.dto.UsuarioUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST para gestión administrativa de usuarios.
 *
 * Endpoints:
 *   GET    /api/usuarios              → Listar usuarios (filtro opcional por empresaId)
 *   GET    /api/usuarios/{id}         → Obtener usuario por ID
 *   PUT    /api/usuarios/{id}         → Actualizar usuario (rol, empresa, depto, etc.)
 *   DELETE /api/usuarios/{id}         → Desactivar usuario (soft delete)
 */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> listarUsuarios(
            @RequestParam(required = false) String empresaId) {
        return ResponseEntity.ok(usuarioService.listarUsuarios(empresaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> obtenerUsuario(@PathVariable String id) {
        return ResponseEntity.ok(usuarioService.obtenerUsuario(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> actualizarUsuario(
            @PathVariable String id,
            @RequestBody UsuarioUpdateRequest request) {
        return ResponseEntity.ok(usuarioService.actualizarUsuario(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivarUsuario(@PathVariable String id) {
        usuarioService.desactivarUsuario(id);
        return ResponseEntity.noContent().build();
    }
}
