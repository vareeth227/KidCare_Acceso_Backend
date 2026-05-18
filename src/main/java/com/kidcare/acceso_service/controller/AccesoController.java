package com.kidcare.acceso_service.controller;

import com.kidcare.acceso_service.dto.AccesoRequestDTO;
import com.kidcare.acceso_service.dto.AccesoResponseDTO;
import com.kidcare.acceso_service.dto.EditarAccesoRequestDTO;
import com.kidcare.acceso_service.service.AccesoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/acceso")
public class AccesoController {

    @Autowired
    private AccesoService accesoService;

    // POST /api/acceso — crea un acceso de delegado sobre un menor (CU007)
    @PostMapping
    public ResponseEntity<AccesoResponseDTO> crear(@Valid @RequestBody AccesoRequestDTO dto,
            Authentication authentication) {
        Integer idTutor = Integer.parseInt(authentication.getName());
        return ResponseEntity.ok(accesoService.crearAcceso(dto, idTutor));
    }

    // PUT /api/acceso/{id} — edita la fecha de expiración del acceso (CU007)
    @PutMapping("/{id}")
    public ResponseEntity<AccesoResponseDTO> editar(@PathVariable Integer id,
            @RequestBody EditarAccesoRequestDTO dto,
            Authentication authentication) {
        Integer idTutor = Integer.parseInt(authentication.getName());
        return ResponseEntity.ok(accesoService.editarAcceso(id, dto, idTutor));
    }

    // DELETE /api/acceso/{id} — revoca el acceso de un delegado (CU007)
    @DeleteMapping("/{id}")
    public ResponseEntity<String> revocar(@PathVariable Integer id,
            Authentication authentication) {
        Integer idTutor = Integer.parseInt(authentication.getName());
        accesoService.revocarAcceso(id, idTutor);
        return ResponseEntity.ok("Acceso revocado correctamente");
    }

    // GET /api/acceso — obtiene todos los accesos del tutor autenticado (CU007)
    @GetMapping
    public ResponseEntity<List<AccesoResponseDTO>> listar(Authentication authentication) {
        Integer idTutor = Integer.parseInt(authentication.getName());
        return ResponseEntity.ok(accesoService.obtenerAccesosPorTutor(idTutor));
    }
}
