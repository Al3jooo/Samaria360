package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web.dto;

import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoRecuperacion;

/** {@code POST /api/v1/auth/recuperacion}: el correo y, opcional, el idioma de la interfaz ({@code es} o {@code en}). */
public record SolicitudRecuperacion(String correo, String idioma) {

    public ComandoRecuperacion aComando() {
        return new ComandoRecuperacion(correo, idioma);
    }
}
