package co.edu.unimagdalena.samaria360.booking.application.port.out;

import co.edu.unimagdalena.samaria360.booking.domain.model.EventoReserva;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbox transaccional de {@code reservas_db} (V7; ADR-011, DEC-291): {@link #registrar} se llama dentro de la
 * transacción de la reserva; el relevo reclama, publica y marca fuera de ella, cada paso en su propia transacción.
 */
public interface OutboxEventosPort {

    void registrar(EventoReserva evento);

    /** Hasta {@code limite} eventos pendientes y vencidos, en orden de id, con un arriendo hasta {@code ahora + arriendo}. */
    List<PublicacionPendiente> reclamar(Instant ahora, Duration arriendo, int limite);

    void marcarPublicado(PublicacionPendiente publicacion, Instant ahora);

    void registrarFallo(PublicacionPendiente publicacion, Instant proximoIntento, String error);

    /** Devuelve a la cola, sin contar un intento, los reclamados que no se llegaron a publicar. */
    void liberar(List<PublicacionPendiente> publicaciones, Instant ahora);

    /** Borra los publicados antes de {@code limite} (retención de 30 días, como DEC-272). */
    int purgarPublicados(Instant limite);
}
