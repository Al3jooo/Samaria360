package co.edu.unimagdalena.samaria360.analytics.infrastructure.config;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.ProcesarEventoUseCase;
import co.edu.unimagdalena.samaria360.analytics.domain.model.EventoEntrante;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cada evento en una transacción de MongoDB (réplica rs0, DEC-281): los efectos y la marca de {@code eventos_procesados}
 * se confirman juntos (ADR-011, punto 5). Si algo falla, nada queda y el mensaje se reintenta.
 */
public class ProcesarEventoTransaccional implements ProcesarEventoUseCase {

    private final ProcesarEventoUseCase delegado;
    private final TransactionTemplate transaccion;

    public ProcesarEventoTransaccional(ProcesarEventoUseCase delegado, TransactionTemplate transaccion) {
        this.delegado = delegado;
        this.transaccion = transaccion;
    }

    @Override
    public boolean procesar(EventoEntrante evento) {
        return Boolean.TRUE.equals(transaccion.execute(estado -> delegado.procesar(evento)));
    }
}
