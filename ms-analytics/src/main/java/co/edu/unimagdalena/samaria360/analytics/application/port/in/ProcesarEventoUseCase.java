package co.edu.unimagdalena.samaria360.analytics.application.port.in;

import co.edu.unimagdalena.samaria360.analytics.domain.model.EventoEntrante;

/** Aplica un evento de RabbitMQ (ADR-011). Idempotente por {@code idEvento}. */
public interface ProcesarEventoUseCase {

    /** @return false si el evento ya se había procesado (duplicado) */
    boolean procesar(EventoEntrante evento);
}
