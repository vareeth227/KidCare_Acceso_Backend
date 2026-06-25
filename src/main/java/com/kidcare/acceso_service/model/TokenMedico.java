package com.kidcare.acceso_service.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

// Entidad que almacena el enlace temporal generado para el médico
@Data
@Entity
@Table(name = "TOKEN_MEDICO")
public class TokenMedico {

    // Identificador único del token
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_token")
    private Integer idToken;

    // Acceso al que pertenece el token
    @ManyToOne
    @JoinColumn(name = "id_acceso", nullable = false)
    private Acceso acceso;

    // Hash único generado con SecureRandom
    @Column(name = "token", nullable = false, unique = true)
    private String token;

    // URL completa del enlace compartible
    @Column(name = "url_acceso", nullable = false)
    private String urlAcceso;

    // Estado del token: activo, expirado o revocado
    @Column(name = "estado_token", nullable = false)
    private String estadoToken;

    // Latitud del tutor al momento de generar el enlace
    @Column(name = "latitud_padre")
    private String latitudPadre;

    // Longitud del tutor al momento de generar el enlace
    @Column(name = "longitud_padre")
    private String longitudPadre;

    // Nombre del médico destinatario (obligatorio)
    @Column(name = "nombre_medico")
    private String nombreMedico;

    // RUT del médico (opcional)
    @Column(name = "rut_medico")
    private String rutMedico;

    // Fecha y hora de creación del token (para expiración de 20 minutos)
    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    // IDs de observaciones seleccionadas, separados por coma. NULL = todas.
    @Column(name = "observacion_ids", columnDefinition = "TEXT")
    private String observacionIds;

    // Nombre del menor (enviado por el tutor al generar el enlace)
    @Column(name = "nombre_menor")
    private String nombreMenor;

    // Nombre del tutor que generó el enlace
    @Column(name = "nombre_tutor")
    private String nombreTutor;

    // Edad del menor en años al momento de generar el enlace
    @Column(name = "edad_menor")
    private Integer edadMenor;
}