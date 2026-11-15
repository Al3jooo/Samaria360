package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import co.edu.unimagdalena.samaria360.analytics.domain.model.EventoAnalitica;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbox transaccional de {@code analytics_db} (colección {@code outbox_eventos}, A002; ADR-011, DEC-300):
 * {@link #registrar} se llama dentro de la transacción de MongoDB del cambio (réplica rs0, DEC-281); el relevo reclama,
 * publica y marca fuera de ella.
 */
public interface OutboxEventosPort {

    void registrar(EventoAnalitica evento);

    /** Hasta {@code limite} eventos pendientes y vencidos, en orden, con un arriendo hasta {@code ahora + arriendo}. */
    List<PublicacionPendiente> reclamar(Instant ahora, Duration arriendo, int limite);

    void marcarPublicado(PublicacionPendiente publicacion, Instant ahora);

    void registrarFallo(PublicacionPendiente publicacion, Instant proximoIntento, String error);

    /** Devuelve a la cola, sin contar un intento, los reclamados que no se llegaron a publicar. */
    void liberar(List<PublicacionPendiente> publicaciones, Instant ahora);

    /** Borra los publicados antes de {@code limite} (retención de 30 días, como DEC-272). */
    int purgarPublicados(Instant limite);
}
