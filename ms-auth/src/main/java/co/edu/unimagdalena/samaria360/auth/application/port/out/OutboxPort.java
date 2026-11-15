package co.edu.unimagdalena.samaria360.auth.application.port.out;

import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoCuentaActualizada;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoPublicable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbox transaccional de {@code auth_db} (DEC-26): {@link #registrar} y {@link #registrarPublicacion} se llaman dentro
 * de la transacción del cambio. Los relevos reclaman, entregan y marcan fuera de ella, cada paso en su propia
 * transacción: el relevo HTTP de las cuentas (ADR-008) y el de RabbitMQ (ADR-011).
 */
public interface OutboxPort {

    /** Inserta el evento y una entrega pendiente por destino. */
    void registrar(EventoCuentaActualizada evento, List<Destino> destinos);

    /** Inserta un evento que solo va a RabbitMQ (auditoría de RF13), con su entrega pendiente. */
    void registrarPublicacion(EventoPublicable evento);

    /**
     * Reclama hasta {@code limite} entregas HTTP vencidas del destino, solo la última de cada usuario (el evento lleva el
     * estado completo, así que las anteriores quedan reemplazadas). Concede un arriendo: hasta {@code ahora + arriendo}
     * nadie más las reclama.
     */
    List<EntregaPendiente> reclamar(Destino destino, Instant ahora, Duration arriendo, int limite);

    /** Marca entregada esta entrega y las anteriores del mismo usuario y destino. */
    void marcarEntregada(Destino destino, EntregaPendiente entrega, Instant ahora);

    void registrarFallo(Destino destino, EntregaPendiente entrega, Instant proximoIntento, String error);

    /** Devuelve a la cola, sin contar un intento, las reclamadas que no se llegaron a entregar. */
    void liberar(Destino destino, List<EntregaPendiente> entregas, Instant ahora);

    /**
     * Reclama hasta {@code limite} publicaciones vencidas para RabbitMQ, <b>todas</b> y en orden de id (sin reemplazar
     * las anteriores de un usuario), con un arriendo como {@link #reclamar}.
     */
    List<PublicacionPendiente> reclamarPublicaciones(Instant ahora, Duration arriendo, int limite);

    /** Marca publicada solo esta publicación. */
    void marcarPublicada(PublicacionPendiente publicacion, Instant ahora);

    void registrarFalloPublicacion(PublicacionPendiente publicacion, Instant proximoIntento, String error);

    /** Devuelve a la cola, sin contar un intento, las reclamadas que no se llegaron a publicar. */
    void liberarPublicaciones(List<PublicacionPendiente> publicaciones, Instant ahora);

    /**
     * Borra los eventos que ya llegaron a todos sus destinos y cuya última entrega es anterior a {@code limite}, con sus
     * entregas (retención del outbox). Un evento con alguna entrega pendiente no se toca.
     *
     * @return cuántos eventos se borraron
     */
    int purgarEntregados(Instant limite);
}
