package com.kidcare.acceso_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

// Manejador global de excepciones para todos los controllers
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Maneja errores de validación de los DTOs
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errores.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errores);
    }

    // Maneja excepciones de negocio lanzadas desde los servicios
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        error.put("error", msg);

        HttpStatus status;
        if (msg.contains("revocado")) {
            status = HttpStatus.FORBIDDEN;           // 403 → frontend: revoked
        } else if (msg.contains("radio") || msg.contains("metros") || msg.contains("km")) {
            status = HttpStatus.FORBIDDEN;           // 403 → frontend: geo
        } else if (msg.contains("expirado") || msg.contains("expiró") ||
                   msg.contains("no encontrado") || msg.contains("no está disponible")) {
            status = HttpStatus.NOT_FOUND;           // 404 → frontend: expired
        } else {
            status = HttpStatus.BAD_REQUEST;         // 400 → frontend: expired (default)
        }
        return ResponseEntity.status(status).body(error);
    }
}