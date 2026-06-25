package com.kidcare.acceso_service.dto;

import lombok.Data;
import java.util.List;

@Data
public class VerificarAccesoResponseDTO {
    private String estado;
    private Integer idMenor;
    private String nombreMedico;
    private String resumen;
    private String tipo;
    private String expiracion;
    private List<String> observacionIds;
    // Datos del menor para la vista web del médico
    private String nombreMenor;
    private String nombreTutor;
    private Integer edadMenor;
    // Hora exacta en que el tutor generó el enlace
    private String horaGenerado;
}
