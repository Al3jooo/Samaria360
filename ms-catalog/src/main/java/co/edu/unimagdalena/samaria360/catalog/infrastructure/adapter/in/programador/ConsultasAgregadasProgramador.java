package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.programador;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultasFichasUseCase;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DEC-283: cada {@code samaria360.consultas.cada} (un minuto) encola en el outbox los conteos de consultas a las fichas
 * ({@code ConsultasAgregadas}); el relevo los publica en RabbitMQ. Al detenerse, intenta encolar los del minuto en curso.
 */
@Component
@ConditionalOnProperty(name = "samaria360.consultas.habilitado", havingValue = "true", matchIfMissing = true)
public class ConsultasAgregadasProgramador {

    private static final Logger LOG = LoggerFactory.getLogger(ConsultasAgregadasProgramador.class);

    private final ConsultasFichasUseCase consultas;

    public ConsultasAgregadasProgramador(ConsultasFichasUseCase consultas) {
        this.consultas = consultas;
    }

    @Scheduled(initialDelayString = "${samaria360.consultas.cada:PT1M}", fixedDelayString = "${samaria360.consultas.cada:PT1M}")
    public void publicar() {
        try {
            consultas.publicar();
        } catch (RuntimeException error) {
            LOG.warn("Consultas agregadas: no se pudieron encolar: {}", error.getMessage());
        }
    }

    @PreDestroy
    public void alDetener() {
        publicar();
    }
}
