package com.orquestia.auth;

import com.orquestia.auth.dto.CrearFuncionarioRequest;
import com.orquestia.auth.dto.UsuarioResponse;
import com.orquestia.auth.dto.UsuarioUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;

    /**
     * POST /api/usuarios
     * El admin crea un funcionario nuevo en su empresa.
     * Spring extrae empresaId del token JWT del admin autenticado.
     */
    @PostMapping
    public ResponseEntity<UsuarioResponse> crearFuncionario(
            @RequestBody CrearFuncionarioRequest request,
            Authentication auth) {
        // auth.getName() devuelve el userId (puesto por JwtAuthenticationFilter)
        Usuario admin = usuarioRepository.findById(auth.getName())
                .orElseThrow(() -> new RuntimeException("Admin no encontrado"));
        return ResponseEntity.ok(usuarioService.crearFuncionario(admin.getEmpresaId(), request));
    }

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
