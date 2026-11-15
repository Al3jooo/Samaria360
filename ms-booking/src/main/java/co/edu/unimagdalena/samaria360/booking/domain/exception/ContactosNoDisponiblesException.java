package co.edu.unimagdalena.samaria360.booking.domain.exception;

/** RF42: ms-auth no respondió los contactos de los asistentes (503 {@code SERVICIO_NO_DISPONIBLE}, {@code ms-auth}). */
public class ContactosNoDisponiblesException extends RuntimeException {

    public ContactosNoDisponiblesException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
