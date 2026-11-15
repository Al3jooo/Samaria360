package co.edu.unimagdalena.samaria360.booking.application.port.out;

import co.edu.unimagdalena.samaria360.booking.domain.model.Asistente;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Reservas Confirmadas del Prestador para un servicio y una fecha (RF42; DEC-312), sin los contactos. */
public interface AsistentesPort {

    /** Tour: las del día (y del horario, si se pide). Alojamiento: las estadías que incluyen la noche de {@code fecha}. */
    List<Asistente> confirmadas(UUID prestador, String idServicio, LocalDate fecha, LocalTime hora);
}
