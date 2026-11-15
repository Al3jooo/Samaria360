package co.edu.unimagdalena.samaria360.analytics.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Correo de RF30 al Prestador por una reserva nueva, encolado en la misma transacción que la notificación (DEC-292): un
 * fallo del SMTP o de ms-auth no lo pierde; se reintenta con espera creciente. {@code datos}: la actividad, la fecha y
 * las personas, como la notificación. {@code id} es nulo hasta guardarlo.
 */
public record CorreoPendiente(String id, UUID idEvento, UUID idPrestador, Map<String, String> datos, int intentos,
                              Instant proximoIntento) {

    public CorreoPendiente {
        Objects.requireNonNull(idEvento, "idEvento");
        Objects.requireNonNull(idPrestador, "idPrestador");
        Objects.requireNonNull(proximoIntento, "proximoIntento");
        datos = datos == null ? Map.of() : Map.copyOf(datos);
    }

    /** Se envía desde ya ({@code ahora}, la hora del procesamiento), no desde la fecha del evento. */
    public static CorreoPendiente de(Notificacion nuevaReserva, Instant ahora) {
        if (nuevaReserva.tipo() != TipoNotificacion.NUEVA_RESERVA) {
            throw new IllegalArgumentException("El correo de RF30 es por una reserva nueva");
        }
        return new CorreoPendiente(null, nuevaReserva.idEvento(), nuevaReserva.usuarioId(), nuevaReserva.datos(), 0,
                ahora);
    }
}
