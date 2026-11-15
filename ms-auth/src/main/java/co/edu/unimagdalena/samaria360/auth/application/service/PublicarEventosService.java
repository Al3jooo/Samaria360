package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.port.in.PublicarEventosUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.out.EntregaFallidaException;
import co.edu.unimagdalena.samaria360.auth.application.port.out.OutboxPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.PublicacionPendiente;
import co.edu.unimagdalena.samaria360.auth.application.port.out.PublicadorEventosPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.auth.domain.service.PoliticaReintento;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Relevo del outbox hacia RabbitMQ (ADR-011, punto 4). A diferencia del relevo HTTP, publica <b>todos</b> los eventos
 * pendientes en orden de id, sin reemplazar los anteriores de un usuario: la auditoría (RF13) necesita cada uno, y el
 * consumidor de CuentaActualizada aplica solo la versión mayor. Ante el primer fallo programa el reintento con espera
 * creciente, devuelve a la cola los que quedaron sin intentar y termina la pasada.
 */
public class PublicarEventosService implements PublicarEventosUseCase {

    /** Mayor que la espera máxima de una confirmación del bróker (5 s). */
    static final Duration ARRIENDO = Duration.ofSeconds(30);
    static final int LOTE = 100;

    private final OutboxPort outbox;
    private final PublicadorEventosPort publicador;
    private final RelojPort reloj;
    private final PoliticaReintento politica;

    public PublicarEventosService(OutboxPort outbox, PublicadorEventosPort publicador, RelojPort reloj,
                                  PoliticaReintento politica) {
        this.outbox = Objects.requireNonNull(outbox);
        this.publicador = Objects.requireNonNull(publicador);
        this.reloj = Objects.requireNonNull(reloj);
        this.politica = Objects.requireNonNull(politica);
    }

    @Override
    public int publicar() {
        List<PublicacionPendiente> pendientes = outbox.reclamarPublicaciones(reloj.ahora(), ARRIENDO, LOTE);
        int publicadas = 0;
        for (int i = 0; i < pendientes.size(); i++) {
            PublicacionPendiente publicacion = pendientes.get(i);
            try {
                publicador.publicar(publicacion);
            } catch (EntregaFallidaException fallo) {
                Instant proximo = reloj.ahora().plus(politica.espera(publicacion.intentos() + 1));
                outbox.registrarFalloPublicacion(publicacion, proximo, mensaje(fallo));
                outbox.liberarPublicaciones(pendientes.subList(i + 1, pendientes.size()), reloj.ahora());
                return publicadas;
            }
            outbox.marcarPublicada(publicacion, reloj.ahora());
            publicadas++;
        }
        return publicadas;
    }

    private static String mensaje(EntregaFallidaException fallo) {
        String texto = fallo.getMessage() == null ? fallo.getClass().getSimpleName() : fallo.getMessage();
        return texto.length() > 500 ? texto.substring(0, 500) : texto;
    }
}
