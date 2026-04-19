package com.orquestia.auth;

import com.orquestia.auth.dto.CrearFuncionarioRequest;
import com.orquestia.auth.dto.UsuarioResponse;
import com.orquestia.auth.dto.UsuarioUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para gestión de usuarios (CRUD).
 * Separado del AuthService para mantener responsabilidades claras:
 *   - AuthService → login/register
 *   - UsuarioService → gestión administrativa de usuarios
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Crea un funcionario nuevo y lo asigna a la empresa y departamento indicados.
     * Solo el admin de la empresa debería llamar esto.
     */
    public UsuarioResponse crearFuncionario(String empresaId, CrearFuncionarioRequest req) {
        if (usuarioRepository.existsByEmail(req.getEmail())) {
            throw new RuntimeException("Ya existe un usuario con ese email");
        }
        Usuario usuario = Usuario.builder()
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .nombre(req.getNombre())
                .apellido(req.getApellido())
                .rol(Rol.FUNCIONARIO)
                .empresaId(empresaId)
                .departamentoId(req.getDepartamentoId())
                .build();
        return toResponse(usuarioRepository.save(usuario));
    }

    /**
     * Lista todos los usuarios, opcionalmente filtrados por empresa.
     */
    public List<UsuarioResponse> listarUsuarios(String empresaId) {
        List<Usuario> usuarios;
        if (empresaId != null && !empresaId.isEmpty()) {
            usuarios = usuarioRepository.findByEmpresaId(empresaId);
        } else {
            usuarios = usuarioRepository.findAll();
        }
        return usuarios.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene un usuario por ID.
     */
    public UsuarioResponse obtenerUsuario(String id) {
        Usuario usuario = findById(id);
        return toResponse(usuario);
    }

    /**
     * Actualiza los datos de un usuario (rol, empresa, departamento, etc.).
     * Solo actualiza los campos que no son null en el request.
     */
    public UsuarioResponse actualizarUsuario(String id, UsuarioUpdateRequest request) {
        Usuario usuario = findById(id);

        if (request.getNombre() != null) {
            usuario.setNombre(request.getNombre());
        }
        if (request.getApellido() != null) {
            usuario.setApellido(request.getApellido());
        }
        if (request.getRol() != null) {
            usuario.setRol(request.getRol());
        }
        if (request.getEmpresaId() != null) {
            usuario.setEmpresaId(request.getEmpresaId());
        }
        if (request.getDepartamentoId() != null) {
            usuario.setDepartamentoId(request.getDepartamentoId());
        }
        if (request.getActivo() != null) {
            usuario.setActivo(request.getActivo());
        }

        usuario = usuarioRepository.save(usuario);
        return toResponse(usuario);
    }

    /**
     * Desactiva un usuario (soft delete).
     */
    public void desactivarUsuario(String id) {
        Usuario usuario = findById(id);
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    // ===== Métodos internos =====

    private Usuario findById(String id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + id));
    }

    /**
     * Convierte modelo → DTO de respuesta (sin password).
     */
    private UsuarioResponse toResponse(Usuario usuario) {
        return UsuarioResponse.builder()
                .id(usuario.getId())
                .email(usuario.getEmail())
                .nombre(usuario.getNombre())
                .apellido(usuario.getApellido())
                .rol(usuario.getRol())
                .empresaId(usuario.getEmpresaId())
                .departamentoId(usuario.getDepartamentoId())
                .activo(usuario.isActivo())
                .fechaCreacion(usuario.getFechaCreacion())
                .build();
    }
}
