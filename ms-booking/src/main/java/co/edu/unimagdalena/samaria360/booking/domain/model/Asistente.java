package co.edu.unimagdalena.samaria360.booking.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Un turista con una reserva Confirmada para la actividad y la fecha (RF42; DEC-312), con su contacto de ms-auth, que
 * no se guarda en {@code reservas_db}: se pide en el momento.
 */
public record Asistente(UUID idReserva, UUID idTurista, String nombre, String correo, String telefono, int personas,
                        LocalTime horaInicio, String nombreUnidad, LocalDate fechaEntrada, LocalDate fechaSalida) {

    public Asistente {
        Objects.requireNonNull(idReserva, "idReserva");
        Objects.requireNonNull(idTurista, "idTurista");
    }

    public Asistente conContacto(ContactoTurista contacto) {
        return contacto == null ? this : new Asistente(idReserva, idTurista, contacto.nombre(), contacto.correo(),
                contacto.telefono(), personas, horaInicio, nombreUnidad, fechaEntrada, fechaSalida);
    }

    @Override
    public String toString() {
        return "Asistente[" + idReserva + ", ****]";
    }
}
