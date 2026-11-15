package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.out.reloj;

import co.edu.unimagdalena.samaria360.analytics.application.port.out.RelojPort;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Hora del sistema; las pruebas la reemplazan. */
@Component
public class RelojSistemaAdapter implements RelojPort {

    @Override
    public Instant ahora() {
        return Instant.now();
    }
}
