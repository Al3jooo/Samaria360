package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Deduplicación por {@code idEvento} (ADR-011, punto 5), en la transacción del efecto. */
public interface EventosProcesadosPort {

    boolean yaProcesado(UUID idEvento);

    void registrar(UUID idEvento, String tipo, Instant procesadoEn);
}
