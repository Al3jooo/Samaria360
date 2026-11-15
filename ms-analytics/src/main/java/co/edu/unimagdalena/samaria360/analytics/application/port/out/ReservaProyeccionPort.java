package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import co.edu.unimagdalena.samaria360.analytics.domain.model.ReservaProyectada;

/** Hechos de las reservas (colección técnica {@code reservas}, DEC-292). */
public interface ReservaProyeccionPort {

    /** Crea o actualiza la reserva con el estado del evento. */
    void guardar(ReservaProyectada reserva);

    /** RF17 (decisión 1): una reserva Completada del turista para el servicio, si la hay. */
    java.util.Optional<ReservaProyectada> completadaDe(java.util.UUID turista, String idServicio);
}
