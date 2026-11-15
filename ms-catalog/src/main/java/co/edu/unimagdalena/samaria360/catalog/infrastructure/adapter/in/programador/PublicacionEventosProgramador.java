package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.programador;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.PublicarEventosUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Relevo del outbox hacia RabbitMQ (ADR-011, DEC-293): cada {@code samaria360.eventos.cada} publica los eventos de
 * reservas pendientes; cada hora borra los publicados hace más de 30 días. RabbitMQ caído no afecta al catálogo.
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

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${samaria360.eventos.purga:PT1H}")
    public void purgar() {
        try {
            int borrados = relevo.purgar();
            if (borrados > 0) {
                LOG.info("Purga del outbox: {} eventos publicados hace más de 30 días", borrados);
            }
        } catch (RuntimeException error) {
            LOG.warn("Purga del outbox: falló y se reintenta: {}", error.getMessage());
        }
    }
}
