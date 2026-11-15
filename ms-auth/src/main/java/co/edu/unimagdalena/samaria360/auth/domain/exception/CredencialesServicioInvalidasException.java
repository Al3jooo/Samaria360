package co.edu.unimagdalena.samaria360.auth.domain.exception;

/** Cliente interno desconocido, deshabilitado o con un secreto incorrecto (401, DEC-280). */
public class CredencialesServicioInvalidasException extends RuntimeException {

    public CredencialesServicioInvalidasException() {
        super("Credenciales de servicio inválidas");
    }
}
