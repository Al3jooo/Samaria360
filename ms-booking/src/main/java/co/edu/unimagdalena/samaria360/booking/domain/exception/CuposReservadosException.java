package co.edu.unimagdalena.samaria360.booking.domain.exception;

/** RF19 (decisión 3 del equipo): los cupos no bajan de lo ya reservado (409 {@code CUPOS_RESERVADOS}). */
public class CuposReservadosException extends RuntimeException {

    private final int reservados;

    public CuposReservadosException(int reservados) {
        super("Hay " + reservados + " cupos reservados");
        this.reservados = reservados;
    }

    public int getReservados() {
        return reservados;
    }
}
