package com.kidcare.acceso_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kidcare.acceso_service.dto.AccesoRequestDTO;
import com.kidcare.acceso_service.model.Acceso;
import com.kidcare.acceso_service.security.JwtFilter;
import com.kidcare.acceso_service.security.JwtUtil;
import com.kidcare.acceso_service.security.SecurityConfig;
import com.kidcare.acceso_service.service.AccesoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({SecurityConfig.class, JwtFilter.class})
@WebMvcTest(AccesoController.class)
class AccesoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccesoService accesoService;

    @MockitoBean
    private JwtUtil jwtUtil;

    // ─── POST /api/acceso ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "1", roles = {"TUTOR"})
    void crear_ConDatosValidos_Retorna200Ok() throws Exception {
        AccesoRequestDTO requestDTO = new AccesoRequestDTO();
        requestDTO.setIdMenor(10);
        requestDTO.setIdUsuarioDelegado(20);
        requestDTO.setFechaExpiracion(LocalDate.now().plusDays(5));

        Acceso mockAcceso = new Acceso();
        mockAcceso.setIdAcceso(1);
        mockAcceso.setIdMenor(10);
        mockAcceso.setIdUsuario(1); // Tutor id

        when(accesoService.crearAcceso(any(AccesoRequestDTO.class), eq(1))).thenReturn(mockAcceso);

        mockMvc.perform(post("/api/acceso")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAcceso").value(1))
                .andExpect(jsonPath("$.idMenor").value(10))
                .andExpect(jsonPath("$.idUsuario").value(1));

        verify(accesoService).crearAcceso(any(AccesoRequestDTO.class), eq(1));
    }

    @Test
    @WithMockUser(username = "1", roles = {"TUTOR"})
    void crear_ConDatosInvalidos_Retorna400BadRequest() throws Exception {
        AccesoRequestDTO requestDTO = new AccesoRequestDTO();
        // Faltan idMenor e idUsuarioDelegado

        mockMvc.perform(post("/api/acceso")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_SinAutenticacion_Retorna403o401() throws Exception {
        AccesoRequestDTO requestDTO = new AccesoRequestDTO();
        requestDTO.setIdMenor(10);
        requestDTO.setIdUsuarioDelegado(20);

        mockMvc.perform(post("/api/acceso")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isForbidden());
    }

    // ─── DELETE /api/acceso/{id} ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = "1", roles = {"TUTOR"})
    void revocar_RetornaMensajeExito() throws Exception {
        mockMvc.perform(delete("/api/acceso/99"))
                .andExpect(status().isOk())
                .andExpect(content().string("Acceso revocado correctamente"));

        verify(accesoService).revocarAcceso(eq(99), eq(1));
    }

    // ─── GET /api/acceso ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "1", roles = {"TUTOR"})
    void listar_RetornaAccesosTutor() throws Exception {
        Acceso mockAcceso = new Acceso();
        mockAcceso.setIdAcceso(1);
        mockAcceso.setIdMenor(10);
        mockAcceso.setIdUsuario(1);

        when(accesoService.obtenerAccesosPorTutor(1)).thenReturn(List.of(mockAcceso));

        mockMvc.perform(get("/api/acceso"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idAcceso").value(1))
                .andExpect(jsonPath("$[0].idMenor").value(10));

        verify(accesoService).obtenerAccesosPorTutor(1);
    }
}
