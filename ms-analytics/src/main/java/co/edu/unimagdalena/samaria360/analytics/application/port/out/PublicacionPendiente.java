package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento reclamado por el relevo de RabbitMQ (ADR-011): {@code idEvento} es el {@code _id} del documento del outbox;
 * {@code idPublico}, el {@code idEvento} del sobre; {@code datosJson}, los {@code datos}; {@code intentos}, los que ya
 * fallaron.
 */
public record PublicacionPendiente(String idEvento, UUID idPublico, String tipo, String clave, Instant ocurridoEn,
                                   String datosJson, int intentos) {
}
