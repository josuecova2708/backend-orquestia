package com.orquestia.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro JWT — intercepta CADA petición HTTP.
 *
 * Flujo:
 *   1. Revisa si hay header "Authorization: Bearer <token>"
 *   2. Si existe, valida el token con JwtService
 *   3. Si es válido, pone al usuario en el SecurityContext de Spring
 *   4. Si no hay token o es inválido, deja pasar (Spring Security decide si la ruta requiere auth)
 *
 * OncePerRequestFilter → garantiza que se ejecuta una sola vez por request
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. Obtener el header Authorization
        String authHeader = request.getHeader("Authorization");

        // 2. Si no hay header o no empieza con "Bearer ", seguir sin autenticar
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Extraer el token (quitar "Bearer ")
        String token = authHeader.substring(7);

        // 4. Validar token
        if (jwtService.isTokenValid(token)) {
            String userId = jwtService.extractUserId(token);
            String rol = jwtService.extractRol(token);

            // 5. Crear autenticación con el rol como authority
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + rol))
            );

            // 6. Poner en el SecurityContext — ahora Spring sabe quién es el usuario
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
