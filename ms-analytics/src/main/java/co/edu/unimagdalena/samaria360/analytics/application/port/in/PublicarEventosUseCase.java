package co.edu.unimagdalena.samaria360.analytics.application.port.in;

/** Relevo del outbox hacia RabbitMQ (ADR-011): una pasada; devuelve cuántos eventos publicó. */
public interface PublicarEventosUseCase {

    int publicar();

    /** Retención de 30 días de los eventos ya publicados; devuelve cuántos borró. */
    int purgar();
}
