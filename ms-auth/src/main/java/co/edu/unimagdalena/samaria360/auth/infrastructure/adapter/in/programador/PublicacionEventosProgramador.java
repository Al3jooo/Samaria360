package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.programador;

import co.edu.unimagdalena.samaria360.auth.application.port.in.PublicarEventosUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Relevo del outbox hacia RabbitMQ (ADR-011): cada {@code samaria360.eventos.cada} publica los eventos pendientes, en su
 * propio hilo del planificador: RabbitMQ caído no retrasa el relevo HTTP de las cuentas (ADR-008).
 */
@Component
@ConditionalOnProperty(name = "samaria360.eventos.habilitado", havingValue = "true", matchIfMissing = true)
public class PublicacionEventosProgramador {

    private static final Logger LOG = LoggerFactory.getLogger(PublicacionEventosProgramador.class);

    private final PublicarEventosUseCase relevo;

    public PublicacionEventosProgramador(PublicarEventosUseCase relevo) {
        this.relevo = relevo;
    }

    @Scheduled(initialDelayString = "${samaria360.eventos.espera-inicial:PT5S}",
            fixedDelayString = "${samaria360.eventos.cada:PT1S}")
    public void publicar() {
        try {
            int publicadas = relevo.publicar();
            if (publicadas > 0) {
                LOG.info("Relevo a RabbitMQ: {} eventos publicados", publicadas);
            }
        } catch (RuntimeException error) {
            LOG.warn("Relevo a RabbitMQ: la pasada falló y se reintenta: {}", error.getMessage());
        }
    }
}
