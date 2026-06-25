package com.kidcare.acceso_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

// DTO que recibe los datos para generar un enlace temporal para el médico
@Data
public class TokenMedicoRequestDTO {

    // ID del menor cuya bitácora se compartirá
    @NotNull(message = "El id del menor es obligatorio")
    private Integer idMenor;

    // Nombre del médico destinatario (obligatorio)
    @NotBlank(message = "El nombre del médico es obligatorio")
    private String nombreMedico;

    // RUT del médico
    @NotBlank(message = "El RUT del médico es obligatorio")
    private String rutMedico;

    // Latitud del tutor al momento de generar el enlace
    @NotBlank(message = "La latitud es obligatoria")
    private String latitudPadre;

    // Longitud del tutor al momento de generar el enlace
    @NotBlank(message = "La longitud es obligatoria")
    private String longitudPadre;

    // IDs de observaciones a compartir. NULL o vacío = todas.
    private List<String> observacionIds;

    // Datos del menor para mostrar en la vista web del médico
    private String nombreMenor;
    private String nombreTutor;
    private Integer edadMenor;
}