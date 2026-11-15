package co.edu.unimagdalena.samaria360.booking.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento reclamado por el relevo de RabbitMQ (ADR-011): {@code idPublico} es el {@code idEvento} del sobre;
 * {@code datosJson}, los {@code datos} guardados en el outbox; {@code intentos}, los que ya fallaron.
 */
public record PublicacionPendiente(long idEvento, UUID idPublico, String tipo, String clave, Instant ocurridoEn,
                                   String datosJson, int intentos) {
}
