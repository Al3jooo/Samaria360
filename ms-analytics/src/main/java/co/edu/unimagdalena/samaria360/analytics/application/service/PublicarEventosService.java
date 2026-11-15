package co.edu.unimagdalena.samaria360.analytics.application.service;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.PublicarEventosUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.PublicacionFallidaException;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.PublicacionPendiente;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.PublicadorEventosPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.analytics.domain.service.PoliticaReintento;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Relevo del outbox hacia RabbitMQ (ADR-011, punto 4; DEC-300): publica los eventos pendientes en orden de id. Ante el
 * primer fallo programa el reintento con espera creciente, devuelve a la cola los que quedaron sin intentar y termina la
 * pasada. Un RabbitMQ caído no afecta a ms-analytics: los eventos esperan en {@code outbox_eventos}.
 */
public class PublicarEventosService implements PublicarEventosUseCase {

    static final Duration ARRIENDO = Duration.ofSeconds(30);
    static final int LOTE = 100;
    static final Duration RETENCION = Duration.ofDays(30);

    private final OutboxEventosPort outbox;
    private final PublicadorEventosPort publicador;
    private final RelojPort reloj;
    private final PoliticaReintento politica;

    public PublicarEventosService(OutboxEventosPort outbox, PublicadorEventosPort publicador, RelojPort reloj,
                                  PoliticaReintento politica) {
        this.outbox = Objects.requireNonNull(outbox);
        this.publicador = Objects.requireNonNull(publicador);
        this.reloj = Objects.requireNonNull(reloj);
        this.politica = Objects.requireNonNull(politica);
    }

    @Override
    public int publicar() {
        List<PublicacionPendiente> pendientes = outbox.reclamar(reloj.ahora(), ARRIENDO, LOTE);
        int publicadas = 0;
        for (int i = 0; i < pendientes.size(); i++) {
            PublicacionPendiente publicacion = pendientes.get(i);
            try {
                publicador.publicar(publicacion);
            } catch (PublicacionFallidaException fallo) {
                Instant proximo = reloj.ahora().plus(politica.espera(publicacion.intentos() + 1));
                String mensaje = fallo.getMessage() == null ? "sin detalle" : fallo.getMessage();
                outbox.registrarFallo(publicacion, proximo, mensaje.length() > 500 ? mensaje.substring(0, 500) : mensaje);
                outbox.liberar(pendientes.subList(i + 1, pendientes.size()), reloj.ahora());
                return publicadas;
            }
            outbox.marcarPublicado(publicacion, reloj.ahora());
            publicadas++;
        }
        return publicadas;
    }

    @Override
    public int purgar() {
        return outbox.purgarPublicados(reloj.ahora().minus(RETENCION));
    }
}
