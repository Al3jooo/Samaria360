package co.edu.unimagdalena.samaria360.analytics.application.port.in;

import co.edu.unimagdalena.samaria360.analytics.domain.model.Calificacion;

import java.util.UUID;

/** RF17 (decisión 1 del equipo, DEC-300): el Turista califica un servicio con su reserva Completada o un atractivo. */
public interface CalificarUseCase {

    record ComandoCalificar(UUID turista, String tipoEntidad, String idEntidad, Integer estrellas, String comentario) {
    }

    /** Si puede calificar y, si ya lo hizo, su calificación. {@code motivo}: por qué no puede, o nulo. */
    record Elegibilidad(boolean puedeCalificar, String motivo, Calificacion propia) {
    }

    Calificacion calificar(ComandoCalificar comando);

    Elegibilidad elegibilidad(UUID turista, String tipoEntidad, String idEntidad);
}
