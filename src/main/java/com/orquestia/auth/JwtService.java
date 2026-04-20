package com.orquestia.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Servicio para generar y validar tokens JWT.
 *
 * JWT (JSON Web Token) es el estándar para autenticación sin estado (stateless).
 * El flujo es:
 *   1. Usuario hace login → backend genera token con sus datos
 *   2. Frontend guarda el token en localStorage
 *   3. En cada petición, frontend envía: Authorization: Bearer <token>
 *   4. Backend valida el token y extrae quién es el usuario
 *
 * @Service → Le dice a Spring que esta clase es un servicio inyectable
 */
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private long expiration;

    /**
     * Genera un token JWT para un usuario (usa el empresaId actual del usuario).
     */
    public String generateToken(Usuario usuario) {
        return generateTokenForEmpresa(usuario, usuario.getEmpresaId());
    }

    /**
     * Genera un token JWT sobreescribiendo el empresaId (para switch de empresa o multi-admin).
     */
    public String generateTokenForEmpresa(Usuario usuario, String empresaId) {
        return Jwts.builder()
                .subject(usuario.getId())
                .claims(Map.of(
                        "email", usuario.getEmail(),
                        "nombre", usuario.getNombre(),
                        "rol", usuario.getRol().name(),
                        "empresaId", empresaId != null ? empresaId : ""
                ))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extrae el userId (subject) del token.
     */
    public String extractUserId(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Extrae el email del token.
     */
    public String extractEmail(String token) {
        return extractClaims(token).get("email", String.class);
    }

    /**
     * Extrae el rol del token.
     */
    public String extractRol(String token) {
        return extractClaims(token).get("rol", String.class);
    }

    /**
     * Verifica si el token es válido (firma correcta + no expirado).
     */
    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
