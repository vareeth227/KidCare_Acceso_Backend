package com.kidcare.acceso_service.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class EditarAccesoRequestDTO {
    private LocalDate fechaExpiracion;
}
