package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.programador;

import co.edu.unimagdalena.samaria360.auth.application.port.in.PurgarOutboxUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purga del outbox (DEC-272): cada {@code samaria360.outbox.purga.cada} (una hora) borra los eventos ya entregados con
 * más de {@code samaria360.outbox.purga.retencion} (30 días). Usa el tercer hilo del programador
 * ({@code spring.task.scheduling.pool.size = 3}): no retrasa al relevo. Los errores van al log y la siguiente pasada
 * vuelve a intentar.
 */
@Component
@ConditionalOnProperty(name = "samaria360.outbox.habilitado", havingValue = "true", matchIfMissing = true)
public class PurgaOutboxProgramador {

    private static final Logger LOG = LoggerFactory.getLogger(PurgaOutboxProgramador.class);

    private final PurgarOutboxUseCase purga;

    public PurgaOutboxProgramador(PurgarOutboxUseCase purga) {
        this.purga = purga;
    }

    @Scheduled(initialDelayString = "${samaria360.outbox.purga.espera-inicial:PT1M}",
            fixedDelayString = "${samaria360.outbox.purga.cada:PT1H}")
    public void purgar() {
        try {
            int borrados = purga.purgar();
            if (borrados > 0) {
                LOG.info("Purga del outbox de cuentas: {} eventos entregados borrados", borrados);
            }
        } catch (RuntimeException error) {
            LOG.warn("Purga del outbox de cuentas: la pasada falló y se reintenta: {}", error.getMessage());
        }
    }
}
