package co.edu.unimagdalena.samaria360.auth.application.fake;

import co.edu.unimagdalena.samaria360.auth.application.port.out.EntregaPendiente;
import co.edu.unimagdalena.samaria360.auth.application.port.out.OutboxPort;
import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoCuentaActualizada;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Outbox en memoria con la semántica del adaptador JDBC: última entrega pendiente por usuario, arriendo y reintentos. */
public class OutboxEnMemoria implements OutboxPort {

    public static final class Entrega {
        public final long idEvento;
        public final Destino destino;
        public final EventoCuentaActualizada evento;
        public int intentos;
        public Instant proximoIntento;
        public Instant entregadoEn;
        public String ultimoError;

        Entrega(long idEvento, Destino destino, EventoCuentaActualizada evento) {
            this.idEvento = idEvento;
            this.destino = destino;
            this.evento = evento;
            this.proximoIntento = evento.ocurridoEn();
        }
    }

    private final List<Entrega> entregas = new ArrayList<>();
    private final List<EventoCuentaActualizada> eventos = new ArrayList<>();
    private RuntimeException fallarAlRegistrar;

    /** Simula un fallo al escribir el evento: lo lanza el siguiente {@link #registrar}. */
    public void fallarAlRegistrar(RuntimeException error) {
        this.fallarAlRegistrar = error;
    }

    public List<EventoCuentaActualizada> eventos() {
        return eventos;
    }

    public List<Entrega> entregas() {
        return entregas;
    }

    public Entrega entrega(Destino destino, long version) {
        return entregas.stream().filter(e -> e.destino == destino && e.evento.version() == version)
                .findFirst().orElseThrow();
    }

    @Override
    public void registrar(EventoCuentaActualizada evento, List<Destino> destinos) {
        if (fallarAlRegistrar != null) {
            throw fallarAlRegistrar;
        }
        eventos.add(evento);
        long id = eventos.size();
        destinos.forEach(destino -> entregas.add(new Entrega(id, destino, evento)));
    }

    @Override
    public List<EntregaPendiente> reclamar(Destino destino, Instant ahora, Duration arriendo, int limite) {
        List<Entrega> pendientes = entregas.stream()
                .filter(e -> e.destino == destino && e.entregadoEn == null).toList();
        List<Entrega> elegidas = pendientes.stream()
                .filter(e -> !e.proximoIntento.isAfter(ahora))
                .filter(e -> pendientes.stream()
                        .filter(o -> o.evento.idUsuario().equals(e.evento.idUsuario()))
                        .mapToLong(o -> o.idEvento).max().orElseThrow() == e.idEvento)
                .sorted(Comparator.comparingLong(e -> e.idEvento))
                .limit(limite)
                .toList();
        elegidas.forEach(e -> e.proximoIntento = ahora.plus(arriendo));
        return elegidas.stream().map(e -> new EntregaPendiente(e.idEvento, e.evento.idUsuario(), e.evento.rol(),
                e.evento.estadoCuenta(), e.evento.version(), e.evento.rnt(), e.intentos)).toList();
    }

    @Override
    public void marcarEntregada(Destino destino, EntregaPendiente entrega, Instant ahora) {
        entregas.stream()
                .filter(e -> e.destino == destino && e.entregadoEn == null
                        && e.evento.idUsuario().equals(entrega.idUsuario()) && e.idEvento <= entrega.idEvento())
                .forEach(e -> e.entregadoEn = ahora);
    }

    @Override
    public void registrarFallo(Destino destino, EntregaPendiente entrega, Instant proximoIntento, String error) {
        Entrega e = buscar(destino, entrega);
        e.intentos++;
        e.proximoIntento = proximoIntento;
        e.ultimoError = error;
    }

    @Override
    public void liberar(Destino destino, List<EntregaPendiente> liberadas, Instant ahora) {
        liberadas.forEach(entrega -> buscar(destino, entrega).proximoIntento = ahora);
    }

    /** Último límite pedido a {@link #purgarEntregados}; la regla real se prueba contra PostgreSQL. */
    public Instant limitePurga;

    @Override
    public int purgarEntregados(Instant limite) {
        limitePurga = limite;
        return 0;
    }

    private Entrega buscar(Destino destino, EntregaPendiente entrega) {
        return entregas.stream().filter(e -> e.destino == destino && e.idEvento == entrega.idEvento())
                .findFirst().orElseThrow();
    }
}
