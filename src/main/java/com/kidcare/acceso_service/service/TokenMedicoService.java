package com.kidcare.acceso_service.service;

import com.kidcare.acceso_service.dto.TokenMedicoRequestDTO;
import com.kidcare.acceso_service.dto.TokenMedicoResponseDTO;
import com.kidcare.acceso_service.dto.VerificarAccesoRequestDTO;
import com.kidcare.acceso_service.model.Acceso;
import com.kidcare.acceso_service.model.LogAccesoMedico;
import com.kidcare.acceso_service.model.TokenMedico;
import com.kidcare.acceso_service.repository.AccesoRepository;
import com.kidcare.acceso_service.repository.LogAccesoMedicoRepository;
import com.kidcare.acceso_service.repository.TokenMedicoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kidcare.acceso_service.dto.VerificarAccesoResponseDTO;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

// Servicio que maneja la generación y validación de enlaces temporales para médicos
@Service
public class TokenMedicoService {

    @Autowired
    private TokenMedicoRepository tokenMedicoRepository;

    @Autowired
    private AccesoRepository accesoRepository;

    @Autowired
    private LogAccesoMedicoRepository logAccesoMedicoRepository;

    @org.springframework.beans.factory.annotation.Value("${historial.service.url:http://localhost:8084}")
    private String historialServiceUrl;

    @org.springframework.beans.factory.annotation.Value("${acceso.medico.url-base:http://localhost:5173/acceso/}")
    private String urlBase;

    // Genera un enlace temporal único para que el médico acceda a la bitácora
    public TokenMedicoResponseDTO generarToken(TokenMedicoRequestDTO dto, Integer idUsuarioTutor) {

        // Busca el acceso del tutor sobre el menor; si no existe lo crea
        // automáticamente
        List<Acceso> accesos = accesoRepository.findByIdUsuario(idUsuarioTutor);
        Acceso acceso = accesos.stream()
                .filter(a -> a.getIdMenor().equals(dto.getIdMenor()))
                .findFirst()
                .orElseGet(() -> {
                    Acceso nuevo = new Acceso();
                    nuevo.setIdUsuario(idUsuarioTutor);
                    nuevo.setIdMenor(dto.getIdMenor());
                    nuevo.setFechaCreacion(LocalDate.now());
                    return accesoRepository.save(nuevo);
                });

        // Invalida el token activo anterior si existe
        List<TokenMedico> tokensActivos = tokenMedicoRepository
                .findByAccesoIdAccesoAndEstadoToken(acceso.getIdAcceso(), "activo");
        tokensActivos.forEach(t -> {
            t.setEstadoToken("revocado");
            tokenMedicoRepository.save(t);
        });

        // Genera token único con SecureRandom
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String tokenValue = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        // Crea el token médico
        TokenMedico tokenMedico = new TokenMedico();
        tokenMedico.setAcceso(acceso);
        tokenMedico.setToken(tokenValue);
        tokenMedico.setUrlAcceso(urlBase + tokenValue);
        tokenMedico.setEstadoToken("activo");
        tokenMedico.setLatitudPadre(dto.getLatitudPadre());
        tokenMedico.setLongitudPadre(dto.getLongitudPadre());
        tokenMedico.setNombreMedico(dto.getNombreMedico());
        tokenMedico.setRutMedico(dto.getRutMedico());
        tokenMedico.setFechaCreacion(LocalDateTime.now());
        if (dto.getObservacionIds() != null && !dto.getObservacionIds().isEmpty()) {
            tokenMedico.setObservacionIds(String.join(",", dto.getObservacionIds()));
        }
        tokenMedico.setNombreMenor(dto.getNombreMenor());
        tokenMedico.setNombreTutor(dto.getNombreTutor());
        tokenMedico.setEdadMenor(dto.getEdadMenor());
        tokenMedicoRepository.save(tokenMedico);

        // Registra el evento de creación en el log
        registrarLog(tokenMedico, "CREACION", null);

        // Retorna el DTO con los datos del enlace
        TokenMedicoResponseDTO response = new TokenMedicoResponseDTO();
        response.setToken(tokenValue);
        response.setUrlAcceso(urlBase + tokenValue);
        response.setNombreMedico(dto.getNombreMedico());
        response.setEstadoToken("activo");
        return response;
    }

    // Verifica que el médico esté dentro del radio de 100 metros del tutor
    public boolean verificarProximidad(VerificarAccesoRequestDTO dto) {

        TokenMedico tokenMedico = tokenMedicoRepository.findByToken(dto.getToken())
                .orElseThrow(() -> new RuntimeException("Token no encontrado"));

        // Verifica que el token esté activo
        if (!tokenMedico.getEstadoToken().equals("activo")) {
            registrarLog(tokenMedico, "ERROR", null);
            if ("revocado".equals(tokenMedico.getEstadoToken())) {
                throw new RuntimeException("El enlace fue revocado por el tutor");
            }
            throw new RuntimeException("El enlace ha expirado o no está disponible");
        }

        // Verifica que el token no haya expirado (20 minutos)
        if (tokenMedico.getFechaCreacion() != null &&
                tokenMedico.getFechaCreacion().plusMinutes(20).isBefore(LocalDateTime.now())) {
            tokenMedico.setEstadoToken("expirado");
            tokenMedicoRepository.save(tokenMedico);
            registrarLog(tokenMedico, "EXPIRADO", null);
            throw new RuntimeException("El enlace ha expirado (validez: 20 minutos)");
        }

        // Calcula la distancia entre el médico y el tutor
        double distancia = calcularDistancia(
                Double.parseDouble(tokenMedico.getLatitudPadre()),
                Double.parseDouble(tokenMedico.getLongitudPadre()),
                Double.parseDouble(dto.getLatitudMedico()),
                Double.parseDouble(dto.getLongitudMedico()));

        if (distancia > 100) {
            registrarLog(tokenMedico, "ERROR", null);
            throw new RuntimeException("El médico no está dentro del radio permitido");
        }

        // Registra el uso exitoso
        registrarLog(tokenMedico, "USO", null);
        return true;
    }

    // Revoca manualmente un token activo
    public void revocarToken(String tokenValue, Integer idUsuarioTutor) {

        TokenMedico tokenMedico = tokenMedicoRepository.findByToken(tokenValue)
                .orElseThrow(() -> new RuntimeException("Token no encontrado"));

        // Verifica que el token pertenezca al tutor
        if (!tokenMedico.getAcceso().getIdUsuario().equals(idUsuarioTutor)) {
            throw new RuntimeException("No tienes permiso para revocar este token");
        }

        tokenMedico.setEstadoToken("revocado");
        tokenMedicoRepository.save(tokenMedico);
        registrarLog(tokenMedico, "EXPIRADO", null);
    }

    // Calcula la distancia en metros entre dos coordenadas usando la fórmula de
    // Haversine
    private double calcularDistancia(double lat1, double lon1, double lat2, double lon2) {
        final int RADIO_TIERRA = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return RADIO_TIERRA * c;
    }

    // Verifica proximidad geográfica y retorna el historial del menor
    public VerificarAccesoResponseDTO verificarYObtenerHistorial(VerificarAccesoRequestDTO dto) {
        verificarProximidad(dto);

        TokenMedico tokenMedico = tokenMedicoRepository.findByToken(dto.getToken())
                .orElseThrow(() -> new RuntimeException("Token no encontrado"));
        Integer idMenor = tokenMedico.getAcceso().getIdMenor();

        VerificarAccesoResponseDTO response = new VerificarAccesoResponseDTO();
        response.setEstado("acceso_concedido");
        response.setIdMenor(idMenor);
        response.setNombreMedico(tokenMedico.getNombreMedico());
        response.setExpiracion(tokenMedico.getFechaCreacion().plusMinutes(20).toString());
        response.setNombreMenor(tokenMedico.getNombreMenor());
        response.setNombreTutor(tokenMedico.getNombreTutor());
        response.setEdadMenor(tokenMedico.getEdadMenor());
        if (tokenMedico.getFechaCreacion() != null) {
            response.setHoraGenerado(tokenMedico.getFechaCreacion().toString());
        }
        if (tokenMedico.getObservacionIds() != null && !tokenMedico.getObservacionIds().isBlank()) {
            response.setObservacionIds(Arrays.asList(tokenMedico.getObservacionIds().split(",")));
        }

        RestTemplate restTemplate = new RestTemplate();

        // Intenta obtener el historial existente
        String resumenTexto = null;
        try {
            String url = historialServiceUrl + "/api/historial/medico/" + idMenor;
            @SuppressWarnings("unchecked")
            Map<String, Object> historial = restTemplate.getForObject(url, Map.class);
            if (historial != null && historial.get("resumen") != null) {
                resumenTexto = historial.get("resumen").toString();
            }
        } catch (Exception e) {
            // No existe historial todavía — lo generamos a continuación
        }

        // Si no hay historial y el token tiene observaciones guardadas, genera uno ahora
        if (resumenTexto == null &&
                tokenMedico.getObservacionIds() != null &&
                !tokenMedico.getObservacionIds().isBlank()) {
            try {
                List<String> ids = Arrays.asList(tokenMedico.getObservacionIds().split(","));
                Map<String, Object> genBody = new HashMap<>();
                genBody.put("idMenor", idMenor);
                genBody.put("idInteracciones", ids);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> req = new HttpEntity<>(genBody, headers);
                @SuppressWarnings("unchecked")
                Map<String, Object> genResp = restTemplate.postForObject(
                        historialServiceUrl + "/api/historial/interno/generar", req, Map.class);
                if (genResp != null && genResp.get("resumen") != null) {
                    resumenTexto = genResp.get("resumen").toString();
                }
            } catch (Exception ex) {
                // Generación fallida — el médico verá "sin resumen"
            }
        }

        if (resumenTexto != null) {
            response.setResumen(resumenTexto);
            response.setTipo("resumen");
        } else {
            response.setTipo("sin_historial");
        }

        return response;
    }

    // Registra un evento en el log de acceso médico
    private void registrarLog(TokenMedico tokenMedico, String tipoEvento, String ip) {
        LogAccesoMedico log = new LogAccesoMedico();
        log.setTokenMedico(tokenMedico);
        log.setFechaEvento(LocalDate.now());
        log.setTipoEvento(tipoEvento);
        log.setIpOpcional(ip);
        logAccesoMedicoRepository.save(log);
    }
}