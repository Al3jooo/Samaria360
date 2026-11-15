package co.edu.unimagdalena.samaria360.analytics.application.port.in;

import co.edu.unimagdalena.samaria360.analytics.domain.model.AccionAuditada;

import java.time.Instant;
import java.util.UUID;

/** Filtros del log de auditoría (RF13): acción, usuario y rango de fechas, todos opcionales. */
public record FiltroAuditoria(AccionAuditada accion, UUID usuario, Instant desde, Instant hasta) {
}
