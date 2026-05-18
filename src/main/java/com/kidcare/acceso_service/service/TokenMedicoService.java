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
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.web.client.RestTemplate;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
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

        // Busca el acceso del tutor sobre el menor
        List<Acceso> accesos = accesoRepository.findByIdUsuario(idUsuarioTutor);
        Acceso acceso = accesos.stream()
                .filter(a -> a.getIdMenor().equals(dto.getIdMenor()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No tienes acceso sobre este menor"));

        // Invalida el token activo anterior si existe
        List<TokenMedico> tokensActivos = tokenMedicoRepository
                .findByAccesoIdAccesoAndEstadoToken(acceso.getIdAcceso(), "activo");
        tokensActivos.forEach(t -> {
            t.setEstadoToken("expirado");
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
        tokenMedicoRepository.save(tokenMedico);

        // Registra el evento de creación en el log
        registrarLog(tokenMedico, "CREACION", null);

        // Retorna el DTO con los datos del enlace, incluyendo QR en Base64
        TokenMedicoResponseDTO response = new TokenMedicoResponseDTO();
        response.setToken(tokenValue);
        response.setUrlAcceso(urlBase + tokenValue);
        response.setNombreMedico(dto.getNombreMedico());
        response.setEstadoToken("activo");
        response.setQrCodeBase64(generarQrBase64(urlBase + tokenValue));
        return response;
    }

    // Genera un QR code como imagen PNG codificada en Base64
    private String generarQrBase64(String contenido) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(contenido, BarcodeFormat.QR_CODE, 300, 300);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    // Verifica que el médico esté dentro del radio de 100 metros del tutor
    public boolean verificarProximidad(VerificarAccesoRequestDTO dto) {

        TokenMedico tokenMedico = tokenMedicoRepository.findByToken(dto.getToken())
                .orElseThrow(() -> new RuntimeException("Token no encontrado"));

        // Verifica que el token esté activo
        if (!tokenMedico.getEstadoToken().equals("activo")) {
            registrarLog(tokenMedico, "ERROR", null);
            throw new RuntimeException("El enlace no está activo");
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
        if (tokenMedico.getObservacionIds() != null && !tokenMedico.getObservacionIds().isBlank()) {
            response.setObservacionIds(Arrays.asList(tokenMedico.getObservacionIds().split(",")));
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = historialServiceUrl + "/api/historial/medico/" + idMenor;
            @SuppressWarnings("unchecked")
            Map<String, Object> historial = restTemplate.getForObject(url, Map.class);
            if (historial != null && historial.get("resumen") != null) {
                response.setResumen(historial.get("resumen").toString());
                response.setTipo("resumen");
            } else {
                response.setTipo("sin_historial");
            }
        } catch (Exception e) {
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