package co.edu.unimagdalena.samaria360.auth.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento reclamado por el relevo de RabbitMQ (ADR-011): {@code idPublico} es el {@code idEvento} del sobre;
 * {@code datosJson}, los {@code datos} tal como se guardaron en el outbox; {@code intentos}, los que ya fallaron.
 */
public record PublicacionPendiente(long idEvento, UUID idPublico, String tipo, UUID clave, Instant ocurridoEn,
                                   String datosJson, int intentos) {
}
