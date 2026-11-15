package co.edu.unimagdalena.samaria360.analytics.application.port.out;

/** ms-auth o el servidor SMTP no respondieron: el correo se reintenta. */
public class ServicioExternoNoDisponibleException extends RuntimeException {

    public ServicioExternoNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
