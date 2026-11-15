package co.edu.unimagdalena.samaria360.catalog.application.service;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.ActualizarCalificacionUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.CalificacionCatalogoPort;
import co.edu.unimagdalena.samaria360.catalog.domain.model.CalificacionEntidad;

import java.util.Objects;

/**
 * Aplica {@code PromedioActualizado} (DEC-302): entrega al menos una vez y en cualquier orden (ADR-011), así que solo
 * avanza con una versión mayor. Una entidad que ya no existe se ignora.
 */
public class ActualizarCalificacionService implements ActualizarCalificacionUseCase {

    private final CalificacionCatalogoPort calificaciones;

    public ActualizarCalificacionService(CalificacionCatalogoPort calificaciones) {
        this.calificaciones = Objects.requireNonNull(calificaciones);
    }

    @Override
    public boolean actualizar(CalificacionEntidad calificacion) {
        return calificaciones.guardarSiMayor(Objects.requireNonNull(calificacion));
    }
}
