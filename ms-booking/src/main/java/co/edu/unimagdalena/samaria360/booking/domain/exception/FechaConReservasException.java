package co.edu.unimagdalena.samaria360.booking.domain.exception;

/** RF19 (decisión 3 del equipo): una fecha u horario con reservas confirmadas no se cierra (409 {@code FECHA_CON_RESERVAS}). */
public class FechaConReservasException extends RuntimeException {

    public FechaConReservasException() {
        super("La fecha tiene reservas confirmadas");
    }
}
