package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import java.time.Instant;

/** Hora actual, para poder probar sin esperar. */
public interface RelojPort {

    Instant ahora();
}
