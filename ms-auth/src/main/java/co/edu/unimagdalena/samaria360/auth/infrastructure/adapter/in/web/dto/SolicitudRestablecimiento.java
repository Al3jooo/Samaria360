package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web.dto;

import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoRestablecimiento;

/** {@code POST /api/v1/auth/recuperacion/restablecimiento}: el token del enlace y la contraseña nueva. */
public record SolicitudRestablecimiento(String token, String contrasena) {

    public ComandoRestablecimiento aComando() {
        return new ComandoRestablecimiento(token, contrasena);
    }

    @Override
    public String toString() {
        return "SolicitudRestablecimiento[****]";
    }
}
