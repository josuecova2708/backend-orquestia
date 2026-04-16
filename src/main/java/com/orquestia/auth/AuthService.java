package com.orquestia.auth;

import com.orquestia.auth.dto.AuthResponse;
import com.orquestia.auth.dto.LoginRequest;
import com.orquestia.auth.dto.RegisterRequest;
import com.orquestia.auth.dto.SetupEmpresaRequest;
import com.orquestia.empresa.Empresa;
import com.orquestia.empresa.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Servicio de autenticación — maneja registro y login.
 *
 * @RequiredArgsConstructor → Lombok genera el constructor con todos los campos final.
 *   Spring inyecta automáticamente las dependencias por constructor.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmpresaRepository empresaRepository;

    /**
     * Registra un nuevo usuario.
     * 1. Verifica que el email no exista
     * 2. Hashea la contraseña con BCrypt
     * 3. Guarda en MongoDB
     * 4. Genera token JWT
     * 5. Retorna AuthResponse con el token
     */
    public AuthResponse register(RegisterRequest request) {
        // Verificar email duplicado
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Ya existe un usuario con ese email");
        }

        // Crear usuario
        Usuario usuario = Usuario.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nombre(request.getNombre())
                .apellido(request.getApellido())
                .rol(request.getRol() != null ? request.getRol() : Rol.FUNCIONARIO)
                .empresaId(request.getEmpresaId())
                .departamentoId(request.getDepartamentoId())
                .build();

        // Guardar en MongoDB
        usuario = usuarioRepository.save(usuario);

        // Generar token
        String token = jwtService.generateToken(usuario);

        return buildAuthResponse(usuario, token);
    }

    /**
     * Login de un usuario existente.
     * 1. Busca por email
     * 2. Verifica contraseña con BCrypt
     * 3. Genera token JWT
     */
    public AuthResponse login(LoginRequest request) {
        // Buscar usuario
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Credenciales inválidas"));

        // Verificar contraseña
        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new RuntimeException("Credenciales inválidas");
        }

        // Verificar que esté activo
        if (!usuario.isActivo()) {
            throw new RuntimeException("Usuario desactivado");
        }

        // Generar token
        String token = jwtService.generateToken(usuario);

        return buildAuthResponse(usuario, token);
    }

    /**
     * Onboarding: crea la empresa del usuario y lo vincula como ADMIN.
     * Se llama una sola vez después del registro, cuando empresaId == null.
     * 1. Crea la Empresa con los datos del request
     * 2. Actualiza el Usuario: asigna empresaId + eleva rol a ADMIN
     * 3. Genera nuevo token con el estado actualizado
     * 4. Retorna el AuthResponse fresco (el frontend lo guarda y reemplaza el anterior)
     */
    public AuthResponse setupEmpresa(String userId, SetupEmpresaRequest request) {
        // Buscar el usuario
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Verificar que aún no tiene empresa
        if (usuario.getEmpresaId() != null) {
            throw new RuntimeException("Este usuario ya tiene una empresa asignada");
        }

        // Crear la empresa
        Empresa empresa = Empresa.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion() != null ? request.getDescripcion() : "")
                .rubro(request.getRubro())
                .creadoPor(userId)
                .build();
        empresa = empresaRepository.save(empresa);

        // Vincular empresa al usuario y subir rol a ADMIN
        usuario.setEmpresaId(empresa.getId());
        usuario.setRol(Rol.ADMIN);
        usuario = usuarioRepository.save(usuario);

        // Generar token nuevo (con el empresaId ahora incluido en los claims)
        String token = jwtService.generateToken(usuario);
        return buildAuthResponse(usuario, token);
    }

    private AuthResponse buildAuthResponse(Usuario usuario, String token) {
        return AuthResponse.builder()
                .token(token)
                .userId(usuario.getId())
                .email(usuario.getEmail())
                .nombre(usuario.getNombre())
                .apellido(usuario.getApellido())
                .rol(usuario.getRol())
                .empresaId(usuario.getEmpresaId())
                .build();
    }
}
