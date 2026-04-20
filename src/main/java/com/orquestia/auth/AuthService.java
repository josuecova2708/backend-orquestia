package com.orquestia.auth;

import com.orquestia.auth.dto.AuthResponse;
import com.orquestia.auth.dto.EmpresaResumen;
import com.orquestia.auth.dto.InvitarAdminRequest;
import com.orquestia.auth.dto.LoginRequest;
import com.orquestia.auth.dto.RegisterRequest;
import com.orquestia.auth.dto.SetupEmpresaRequest;
import com.orquestia.empresa.Empresa;
import com.orquestia.empresa.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmpresaRepository empresaRepository;

    public AuthResponse register(RegisterRequest request) {
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Ya existe un usuario con ese email");
        }

        Usuario usuario = Usuario.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nombre(request.getNombre())
                .apellido(request.getApellido())
                .rol(request.getRol() != null ? request.getRol() : Rol.FUNCIONARIO)
                .empresaId(request.getEmpresaId())
                .departamentoId(request.getDepartamentoId())
                .build();

        usuario = usuarioRepository.save(usuario);
        String token = jwtService.generateToken(usuario);
        return buildAuthResponse(usuario, token);
    }

    public AuthResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Credenciales inválidas"));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new RuntimeException("Credenciales inválidas");
        }

        if (!usuario.isActivo()) {
            throw new RuntimeException("Usuario desactivado");
        }

        List<String> empresasAdminIds = usuario.getEmpresasAdmin();
        List<EmpresaResumen> empresas = buildEmpresaResumenes(empresasAdminIds);

        // ADMIN siempre pasa por el selector de empresa (empresaId vacío en token)
        if (usuario.getRol() == Rol.ADMIN && empresasAdminIds != null && !empresasAdminIds.isEmpty()) {
            String token = jwtService.generateTokenForEmpresa(usuario, "");
            return buildAuthResponse(usuario, token, "", empresas);
        }

        String token = jwtService.generateToken(usuario);
        return buildAuthResponse(usuario, token, usuario.getEmpresaId(), empresas);
    }

    /**
     * Crea una nueva empresa y la vincula al usuario.
     * Permite crear múltiples empresas (para multi-admin).
     */
    public AuthResponse setupEmpresa(String userId, SetupEmpresaRequest request) {
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Empresa empresa = Empresa.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion() != null ? request.getDescripcion() : "")
                .rubro(request.getRubro())
                .creadoPor(userId)
                .build();
        empresa = empresaRepository.save(empresa);

        if (usuario.getEmpresasAdmin() == null) {
            usuario.setEmpresasAdmin(new ArrayList<>());
        }
        usuario.getEmpresasAdmin().add(empresa.getId());
        usuario.setEmpresaId(empresa.getId());
        if (usuario.getRol() != Rol.ADMIN) {
            usuario.setRol(Rol.ADMIN);
        }
        usuario = usuarioRepository.save(usuario);

        String token = jwtService.generateToken(usuario);
        return buildAuthResponse(usuario, token, usuario.getEmpresaId(),
                buildEmpresaResumenes(usuario.getEmpresasAdmin()));
    }

    /**
     * Cambia la empresa activa del admin y genera un nuevo token con ese empresaId.
     */
    public AuthResponse switchEmpresa(String userId, String empresaId) {
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<String> admins = usuario.getEmpresasAdmin();
        if (admins == null || !admins.contains(empresaId)) {
            throw new RuntimeException("No tienes acceso a esta empresa");
        }

        usuario.setEmpresaId(empresaId);
        usuario = usuarioRepository.save(usuario);

        String token = jwtService.generateTokenForEmpresa(usuario, empresaId);
        return buildAuthResponse(usuario, token, empresaId,
                buildEmpresaResumenes(admins));
    }

    /**
     * Invita a otro usuario como co-admin de una empresa.
     * Si el email ya existe → agrega la empresa a su lista.
     * Si no existe → crea la cuenta con la empresa asignada.
     */
    public AuthResponse invitarAdmin(String empresaId, InvitarAdminRequest req, String invitadoPorUserId) {
        Usuario invitador = usuarioRepository.findById(invitadoPorUserId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (invitador.getEmpresasAdmin() == null || !invitador.getEmpresasAdmin().contains(empresaId)) {
            throw new RuntimeException("No tienes acceso a esta empresa");
        }

        Usuario invitado;
        if (usuarioRepository.existsByEmail(req.getEmail())) {
            invitado = usuarioRepository.findByEmail(req.getEmail())
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            if (invitado.getEmpresasAdmin() == null) {
                invitado.setEmpresasAdmin(new ArrayList<>());
            }
            if (!invitado.getEmpresasAdmin().contains(empresaId)) {
                invitado.getEmpresasAdmin().add(empresaId);
            }
            invitado.setRol(Rol.ADMIN);
        } else {
            invitado = Usuario.builder()
                    .email(req.getEmail())
                    .password(passwordEncoder.encode(req.getPassword()))
                    .nombre(req.getNombre())
                    .apellido(req.getApellido())
                    .rol(Rol.ADMIN)
                    .empresaId(empresaId)
                    .empresasAdmin(new ArrayList<>(List.of(empresaId)))
                    .build();
        }
        invitado = usuarioRepository.save(invitado);

        return buildAuthResponse(invitado, "", empresaId,
                buildEmpresaResumenes(invitado.getEmpresasAdmin()));
    }

    private List<EmpresaResumen> buildEmpresaResumenes(List<String> empresaIds) {
        if (empresaIds == null || empresaIds.isEmpty()) return List.of();
        return empresaRepository.findAllById(empresaIds).stream()
                .map(e -> new EmpresaResumen(e.getId(), e.getNombre()))
                .collect(Collectors.toList());
    }

    private AuthResponse buildAuthResponse(Usuario usuario, String token) {
        return buildAuthResponse(usuario, token, usuario.getEmpresaId(), List.of());
    }

    private AuthResponse buildAuthResponse(Usuario usuario, String token, String empresaId,
                                            List<EmpresaResumen> empresas) {
        return AuthResponse.builder()
                .token(token)
                .userId(usuario.getId())
                .email(usuario.getEmail())
                .nombre(usuario.getNombre())
                .apellido(usuario.getApellido())
                .rol(usuario.getRol())
                .empresaId(empresaId)
                .departamentoId(usuario.getDepartamentoId())
                .empresasAdmin(empresas)
                .build();
    }
}
