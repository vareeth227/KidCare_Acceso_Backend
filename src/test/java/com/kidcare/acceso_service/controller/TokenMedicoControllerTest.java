package com.kidcare.acceso_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kidcare.acceso_service.dto.TokenMedicoRequestDTO;
import com.kidcare.acceso_service.dto.TokenMedicoResponseDTO;
import com.kidcare.acceso_service.dto.VerificarAccesoRequestDTO;
import com.kidcare.acceso_service.dto.VerificarAccesoResponseDTO;
import com.kidcare.acceso_service.security.JwtFilter;
import com.kidcare.acceso_service.security.JwtUtil;
import com.kidcare.acceso_service.security.SecurityConfig;
import com.kidcare.acceso_service.service.TokenMedicoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import({SecurityConfig.class, JwtFilter.class})
@WebMvcTest(TokenMedicoController.class)
class TokenMedicoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TokenMedicoService tokenMedicoService;

    @MockitoBean
    private JwtUtil jwtUtil;

    // ─── POST /api/acceso/medico/generar ──────────────────────────────────────────

    @Test
    @WithMockUser(username = "1", roles = {"TUTOR"})
    void generar_ConDatosValidos_Retorna200Ok() throws Exception {
        TokenMedicoRequestDTO requestDTO = new TokenMedicoRequestDTO();
        requestDTO.setIdMenor(10);
        requestDTO.setNombreMedico("Dr. House");
        requestDTO.setRutMedico("12.345.678-9");
        requestDTO.setLatitudPadre("-33.4569");
        requestDTO.setLongitudPadre("-70.6483");

        TokenMedicoResponseDTO responseDTO = new TokenMedicoResponseDTO();
        responseDTO.setToken("uuid-token-123");
        responseDTO.setUrlAcceso("http://localhost:8082/acceso/uuid-token-123");

        when(tokenMedicoService.generarToken(any(TokenMedicoRequestDTO.class), eq(1))).thenReturn(responseDTO);

        mockMvc.perform(post("/api/acceso/medico/generar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("uuid-token-123"))
                .andExpect(jsonPath("$.urlAcceso").value("http://localhost:8082/acceso/uuid-token-123"));

        verify(tokenMedicoService).generarToken(any(TokenMedicoRequestDTO.class), eq(1));
    }

    @Test
    @WithMockUser(username = "1", roles = {"TUTOR"})
    void generar_ConDatosInvalidos_Retorna400BadRequest() throws Exception {
        TokenMedicoRequestDTO requestDTO = new TokenMedicoRequestDTO();
        // Faltan campos obligatorios

        mockMvc.perform(post("/api/acceso/medico/generar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generar_SinAutenticacion_Retorna400BadRequest() throws Exception {
        TokenMedicoRequestDTO requestDTO = new TokenMedicoRequestDTO();
        requestDTO.setIdMenor(10);
        requestDTO.setNombreMedico("Dr. House");
        requestDTO.setRutMedico("12.345.678-9");
        requestDTO.setLatitudPadre("-33.4569");
        requestDTO.setLongitudPadre("-70.6483");

        mockMvc.perform(post("/api/acceso/medico/generar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /api/acceso/medico/verificar (PermitAll) ───────────────────────────

    @Test
    void verificar_SinAutenticacion_Permitido_Retorna200Ok() throws Exception {
        VerificarAccesoRequestDTO requestDTO = new VerificarAccesoRequestDTO();
        requestDTO.setToken("uuid-token-123");
        requestDTO.setLatitudMedico("-33.4573");
        requestDTO.setLongitudMedico("-70.6488");

        VerificarAccesoResponseDTO responseDTO = new VerificarAccesoResponseDTO();
        responseDTO.setNombreMedico("Dr. House");
        responseDTO.setNombreMenor("Pedrito");

        when(tokenMedicoService.verificarYObtenerHistorial(any(VerificarAccesoRequestDTO.class))).thenReturn(responseDTO);

        mockMvc.perform(post("/api/acceso/medico/verificar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreMedico").value("Dr. House"))
                .andExpect(jsonPath("$.nombreMenor").value("Pedrito"));

        verify(tokenMedicoService).verificarYObtenerHistorial(any(VerificarAccesoRequestDTO.class));
    }

    @Test
    void verificar_ConDatosInvalidos_Retorna400BadRequest() throws Exception {
        VerificarAccesoRequestDTO requestDTO = new VerificarAccesoRequestDTO();
        // Faltan coordenadas y token

        mockMvc.perform(post("/api/acceso/medico/verificar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isBadRequest());
    }

    // ─── DELETE /api/acceso/medico/revocar/{token} ───────────────────────────────

    @Test
    @WithMockUser(username = "1", roles = {"TUTOR"})
    void revocar_RetornaMensajeExito() throws Exception {
        mockMvc.perform(delete("/api/acceso/medico/revocar/uuid-token-123"))
                .andExpect(status().isOk())
                .andExpect(content().string("Token revocado correctamente"));

        verify(tokenMedicoService).revocarToken(eq("uuid-token-123"), eq(1));
    }
}
