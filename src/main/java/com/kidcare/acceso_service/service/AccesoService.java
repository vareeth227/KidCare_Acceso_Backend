package com.kidcare.acceso_service.service;

import com.kidcare.acceso_service.dto.AccesoRequestDTO;
import com.kidcare.acceso_service.dto.AccesoResponseDTO;
import com.kidcare.acceso_service.dto.EditarAccesoRequestDTO;
import com.kidcare.acceso_service.model.Acceso;
import com.kidcare.acceso_service.model.Delegado;
import com.kidcare.acceso_service.repository.AccesoRepository;
import com.kidcare.acceso_service.repository.DelegadoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

// Servicio que maneja la gestión de accesos de delegados
@Service
public class AccesoService {

    @Autowired
    private AccesoRepository accesoRepository;

    @Autowired
    private DelegadoRepository delegadoRepository;

    // Crea un acceso de delegado sobre un menor
    public AccesoResponseDTO crearAcceso(AccesoRequestDTO dto, Integer idUsuarioTutor) {

        Acceso acceso = new Acceso();
        acceso.setIdMenor(dto.getIdMenor());
        acceso.setIdUsuario(idUsuarioTutor);
        acceso.setFechaCreacion(LocalDate.now());
        acceso.setFechaExpiracion(dto.getFechaExpiracion());
        accesoRepository.save(acceso);

        Delegado delegado = new Delegado();
        delegado.setAcceso(acceso);
        delegado.setIdUsuarioDelegado(dto.getIdUsuarioDelegado());
        delegadoRepository.save(delegado);

        return mapToDTO(acceso, dto.getIdUsuarioDelegado());
    }

    // Edita la fecha de expiración de un acceso existente (CU007)
    public AccesoResponseDTO editarAcceso(Integer idAcceso, EditarAccesoRequestDTO dto, Integer idUsuarioTutor) {

        Acceso acceso = accesoRepository.findById(idAcceso)
                .orElseThrow(() -> new RuntimeException("Acceso no encontrado"));

        if (!acceso.getIdUsuario().equals(idUsuarioTutor)) {
            throw new RuntimeException("No tienes permiso para editar este acceso");
        }

        acceso.setFechaExpiracion(dto.getFechaExpiracion());
        accesoRepository.save(acceso);

        List<Delegado> delegados = delegadoRepository.findByAccesoIdAcceso(idAcceso);
        Integer idDelegado = delegados.isEmpty() ? null : delegados.get(0).getIdUsuarioDelegado();
        return mapToDTO(acceso, idDelegado);
    }

    // Revoca el acceso de un delegado eliminando el registro
    public void revocarAcceso(Integer idAcceso, Integer idUsuarioTutor) {

        Acceso acceso = accesoRepository.findById(idAcceso)
                .orElseThrow(() -> new RuntimeException("Acceso no encontrado"));

        // Verifica que el acceso pertenezca al tutor
        if (!acceso.getIdUsuario().equals(idUsuarioTutor)) {
            throw new RuntimeException("No tienes permiso para revocar este acceso");
        }

        // Elimina los delegados vinculados y luego el acceso
        List<Delegado> delegados = delegadoRepository.findByAccesoIdAcceso(idAcceso);
        delegadoRepository.deleteAll(delegados);
        accesoRepository.delete(acceso);
    }

    // Obtiene todos los accesos de un tutor
    public List<AccesoResponseDTO> obtenerAccesosPorTutor(Integer idUsuarioTutor) {
        return accesoRepository.findByIdUsuario(idUsuarioTutor).stream()
                .map(a -> {
                    List<Delegado> delegados = delegadoRepository.findByAccesoIdAcceso(a.getIdAcceso());
                    Integer idDelegado = delegados.isEmpty() ? null : delegados.get(0).getIdUsuarioDelegado();
                    return mapToDTO(a, idDelegado);
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private AccesoResponseDTO mapToDTO(Acceso acceso, Integer idUsuarioDelegado) {
        AccesoResponseDTO dto = new AccesoResponseDTO();
        dto.setIdAcceso(acceso.getIdAcceso());
        dto.setIdMenor(acceso.getIdMenor());
        dto.setIdUsuarioDelegado(idUsuarioDelegado);
        dto.setFechaCreacion(acceso.getFechaCreacion());
        dto.setFechaExpiracion(acceso.getFechaExpiracion());
        return dto;
    }
}