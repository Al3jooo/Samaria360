package co.edu.unimagdalena.samaria360.chatbot.application.port.out;

import java.time.Instant;

/** Hora actual, para poder probar sin esperar. */
public interface RelojPort {

    Instant ahora();
}
