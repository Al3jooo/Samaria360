package co.edu.unimagdalena.samaria360.analytics.domain.exception;

/** El evento no cumple el contrato de {@code eventos.md}: no se reintenta y va a la cola de mensajes fallidos. */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
