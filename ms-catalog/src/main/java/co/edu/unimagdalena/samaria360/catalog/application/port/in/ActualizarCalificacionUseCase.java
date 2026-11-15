package co.edu.unimagdalena.samaria360.catalog.application.port.in;

import co.edu.unimagdalena.samaria360.catalog.domain.model.CalificacionEntidad;

/** {@code PromedioActualizado} de ms-analytics (DEC-302): copia el promedio en el servicio o el atractivo. */
public interface ActualizarCalificacionUseCase {

    /** Idempotente: una versión igual o menor que la guardada se ignora. {@code true} si se aplicó. */
    boolean actualizar(CalificacionEntidad calificacion);
}
