package co.edu.unimagdalena.samaria360.catalog.application.port.out;

/** RabbitMQ no confirmó el evento: el relevo lo reintenta con espera creciente. */
public class PublicacionFallidaException extends RuntimeException {

    public PublicacionFallidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }

    public PublicacionFallidaException(String mensaje) {
        super(mensaje);
    }
}
