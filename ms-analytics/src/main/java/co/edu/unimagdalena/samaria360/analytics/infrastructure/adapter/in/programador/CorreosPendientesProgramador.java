package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.programador;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.EnviarCorreosPendientesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RF30: cada {@code samaria360.correos.cada} envía los correos encolados al Prestador (DEC-292). */
@Component
@ConditionalOnProperty(name = "samaria360.correos.habilitado", havingValue = "true", matchIfMissing = true)
public class CorreosPendientesProgramador {

    private static final Logger LOG = LoggerFactory.getLogger(CorreosPendientesProgramador.class);

    private final EnviarCorreosPendientesUseCase correos;

    public CorreosPendientesProgramador(EnviarCorreosPendientesUseCase correos) {
        this.correos = correos;
    }

    @Scheduled(initialDelayString = "${samaria360.correos.espera-inicial:PT10S}",
            fixedDelayString = "${samaria360.correos.cada:PT5S}")
    public void enviar() {
        try {
            int enviados = correos.enviar();
            if (enviados > 0) {
                LOG.info("RF30: {} correos enviados a prestadores", enviados);
            }
        } catch (RuntimeException error) {
            LOG.warn("RF30: la pasada de correos falló y se reintenta: {}", error.getMessage());
        }
    }
}
