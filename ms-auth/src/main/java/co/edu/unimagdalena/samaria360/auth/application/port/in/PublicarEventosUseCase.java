package co.edu.unimagdalena.samaria360.auth.application.port.in;

/** Relevo del outbox hacia RabbitMQ (ADR-011): una pasada; devuelve cuántos eventos publicó. */
public interface PublicarEventosUseCase {

    int publicar();
}
