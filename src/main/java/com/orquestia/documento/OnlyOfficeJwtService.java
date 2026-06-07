package com.orquestia.documento;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class OnlyOfficeJwtService {

    private final SecretKey signingKey;

    public OnlyOfficeJwtService(@Value("${onlyoffice.jwt-secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Genera el token que OnlyOffice espera en el campo "token" del config JSON
    public String generarToken(Map<String, Object> payload) {
        return Jwts.builder()
            .claims(payload)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 60L * 60 * 1000)) // 1 hora
            .signWith(signingKey)
            .compact();
    }
}
