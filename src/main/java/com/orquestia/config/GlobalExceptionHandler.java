package com.orquestia.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Manejo global de excepciones.
 *
 * @RestControllerAdvice → Intercepta excepciones de TODOS los controllers
 *   y las convierte en respuestas JSON limpias en vez de stack traces feos.
 *
 * Sin esto, cuando algo falla Spring devuelve HTML/stack trace.
 * Con esto, siempre devuelve JSON como: { "error": "...", "status": 400 }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Errores de validación (@Valid en DTOs) → 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> response = Map.of(
                "status", 400,
                "error", "Error de validación",
                "details", errors,
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * RuntimeException genérica → 400 Bad Request
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        Map<String, Object> response = Map.of(
                "status", 400,
                "error", ex.getMessage(),
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Cualquier otra excepción → 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        Map<String, Object> response = Map.of(
                "status", 500,
                "error", "Error interno del servidor",
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
