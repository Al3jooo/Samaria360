package co.edu.unimagdalena.samaria360.analytics.domain.exception;

/** La notificación no existe o no es del usuario autenticado (404 {@code NOTIFICACION_NO_ENCONTRADA}). */
public class NotificacionNoEncontradaException extends RuntimeException {

    public NotificacionNoEncontradaException() {
        super("Notificación no encontrada");
    }
}
