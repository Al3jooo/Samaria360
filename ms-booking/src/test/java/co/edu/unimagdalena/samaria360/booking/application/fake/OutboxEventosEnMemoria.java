package co.edu.unimagdalena.samaria360.booking.application.fake;

import co.edu.unimagdalena.samaria360.booking.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.PublicacionPendiente;
import co.edu.unimagdalena.samaria360.booking.domain.model.EventoReserva;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Doble del outbox de {@code reservas_db} (ADR-011); el SQL se prueba contra PostgreSQL. */
public class OutboxEventosEnMemoria implements OutboxEventosPort {

    public static final class Pendiente {
        public final long idEvento;
        public final UUID idPublico = UUID.randomUUID();
        public final EventoReserva evento;
        public int intentos;
        public Instant proximoIntento;
        public Instant publicadoEn;
        public String ultimoError;

        Pendiente(long idEvento, EventoReserva evento) {
            this.idEvento = idEvento;
            this.evento = evento;
            this.proximoIntento = evento.ocurridoEn();
        }
    }

    private final List<Pendiente> eventos = new ArrayList<>();
    public Instant limitePurga;

    public List<Pendiente> pendientes() {
        return eventos;
    }

    public List<EventoReserva> eventos() {
        return eventos.stream().map(p -> p.evento).toList();
    }

    @Override
    public void registrar(EventoReserva evento) {
        eventos.add(new Pendiente(eventos.size() + 1L, evento));
    }

    @Override
    public List<PublicacionPendiente> reclamar(Instant ahora, Duration arriendo, int limite) {
        List<Pendiente> elegidos = eventos.stream()
                .filter(p -> p.publicadoEn == null && !p.proximoIntento.isAfter(ahora))
                .sorted(Comparator.comparingLong(p -> p.idEvento)).limit(limite).toList();
        elegidos.forEach(p -> p.proximoIntento = ahora.plus(arriendo));
        return elegidos.stream().map(p -> new PublicacionPendiente(p.idEvento, p.idPublico, p.evento.tipo().name(),
                p.evento.reserva().idReserva().toString(), p.evento.ocurridoEn(), "{}", p.intentos)).toList();
    }

    @Override
    public void marcarPublicado(PublicacionPendiente publicacion, Instant ahora) {
        buscar(publicacion).publicadoEn = ahora;
    }

    @Override
    public void registrarFallo(PublicacionPendiente publicacion, Instant proximoIntento, String error) {
        Pendiente p = buscar(publicacion);
        p.intentos++;
        p.proximoIntento = proximoIntento;
        p.ultimoError = error;
    }

    @Override
    public void liberar(List<PublicacionPendiente> publicaciones, Instant ahora) {
        publicaciones.forEach(publicacion -> buscar(publicacion).proximoIntento = ahora);
    }

    @Override
    public int purgarPublicados(Instant limite) {
        limitePurga = limite;
        return 0;
    }

    private Pendiente buscar(PublicacionPendiente publicacion) {
        return eventos.stream().filter(p -> p.idEvento == publicacion.idEvento()).findFirst().orElseThrow();
    }
}
