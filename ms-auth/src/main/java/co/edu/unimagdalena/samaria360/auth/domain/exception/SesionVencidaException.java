package co.edu.unimagdalena.samaria360.auth.domain.exception;

/** La sesión ya superó su duración máxima desde el inicio de sesión y no se renueva (DEC-317). */
public class SesionVencidaException extends RuntimeException {

    public SesionVencidaException() {
        super("La sesión superó su duración máxima: inicia sesión de nuevo");
    }
}
