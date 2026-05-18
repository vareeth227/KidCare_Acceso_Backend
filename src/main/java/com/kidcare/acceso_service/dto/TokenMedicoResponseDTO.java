package com.kidcare.acceso_service.dto;

import lombok.Data;

// DTO que retorna los datos del enlace temporal generado para el médico
@Data
public class TokenMedicoResponseDTO {

    // Token único generado
    private String token;

    // URL completa del enlace compartible
    private String urlAcceso;

    // Nombre del médico destinatario
    private String nombreMedico;

    // Estado del token: activo, expirado o revocado
    private String estadoToken;

    // Imagen QR en Base64 (PNG) que apunta a urlAcceso
    private String qrCodeBase64;
}