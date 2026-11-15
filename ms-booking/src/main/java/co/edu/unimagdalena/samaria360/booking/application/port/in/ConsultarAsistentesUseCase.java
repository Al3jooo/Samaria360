package co.edu.unimagdalena.samaria360.booking.application.port.in;

import co.edu.unimagdalena.samaria360.booking.domain.model.Asistente;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** RF42 (DEC-312): los turistas con reserva Confirmada para una actividad y fecha del Prestador, con su contacto. */
public interface ConsultarAsistentesUseCase {

    record ListadoAsistentes(String idServicio, LocalDate fecha, LocalTime horaInicio, List<Asistente> asistentes,
                             int totalPersonas) {
    }

    ListadoAsistentes asistentes(UUID prestador, String idServicio, LocalDate fecha, LocalTime hora);
}
