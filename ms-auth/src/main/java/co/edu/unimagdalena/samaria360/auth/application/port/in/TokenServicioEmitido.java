package co.edu.unimagdalena.samaria360.auth.application.port.in;

import java.time.Instant;

/** JWT de servicio emitido para un cliente interno (DEC-280) y su vencimiento. */
public record TokenServicioEmitido(String token, Instant expiraEn) {
}
