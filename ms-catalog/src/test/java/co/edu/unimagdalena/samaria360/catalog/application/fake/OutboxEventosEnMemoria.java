package co.edu.unimagdalena.samaria360.catalog.application.fake;

import co.edu.unimagdalena.samaria360.catalog.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.PublicacionPendiente;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EventoCatalogo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Doble del outbox de catalog_db (ADR-011): guarda los eventos registrados; el relevo se prueba contra MongoDB. */
public class OutboxEventosEnMemoria implements OutboxEventosPort {

    private final List<EventoCatalogo> eventos = new ArrayList<>();

    public List<EventoCatalogo> eventos() {
        return eventos;
    }

    @Override
    public void registrar(EventoCatalogo evento) {
        eventos.add(evento);
    }

    @Override
    public List<PublicacionPendiente> reclamar(Instant ahora, Duration arriendo, int limite) {
        return List.of();
    }

    @Override
    public void marcarPublicado(PublicacionPendiente publicacion, Instant ahora) {
    }

    @Override
    public void registrarFallo(PublicacionPendiente publicacion, Instant proximoIntento, String error) {
    }

    @Override
    public void liberar(List<PublicacionPendiente> publicaciones, Instant ahora) {
    }

    @Override
    public int purgarPublicados(Instant limite) {
        return 0;
    }
}
