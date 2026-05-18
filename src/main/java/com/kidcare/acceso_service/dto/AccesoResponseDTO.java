package com.kidcare.acceso_service.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class AccesoResponseDTO {
    private Integer idAcceso;
    private Integer idMenor;
    private Integer idUsuarioDelegado;
    private LocalDate fechaCreacion;
    private LocalDate fechaExpiracion;
}
