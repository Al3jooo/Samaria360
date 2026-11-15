package co.edu.unimagdalena.samaria360.analytics.application.port.in;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** RF56: el Prestador del token y los filtros opcionales por servicio y fecha ({@code hasta} excluido). */
public record FiltroCalificacionesPrestador(UUID prestador, String idServicio, Instant desde, Instant hasta) {

    public FiltroCalificacionesPrestador {
        Objects.requireNonNull(prestador, "prestador");
    }
}
