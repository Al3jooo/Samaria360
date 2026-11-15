package co.edu.unimagdalena.samaria360.booking.application.service;

import co.edu.unimagdalena.samaria360.booking.application.port.in.CompletarReservasUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.ReservaRepositoryPort;
import co.edu.unimagdalena.samaria360.booking.domain.model.EventoReserva;
import co.edu.unimagdalena.samaria360.booking.domain.model.Reserva;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * RF52 con la hora de {@link RelojPort}: el mismo reloj que decide RF44, así las pruebas no esperan. Cada reserva que
 * pasa a Completada encola su evento en la misma transacción (ADR-011, DEC-291): con él, ms-analytics habilita la
 * calificación (RF17) y avisa al Turista (RF63).
 */
public class CompletarReservasService implements CompletarReservasUseCase {

    private final ReservaRepositoryPort reservas;
    private final RelojPort reloj;
    private final OutboxEventosPort outbox;

    public CompletarReservasService(ReservaRepositoryPort reservas, RelojPort reloj, OutboxEventosPort outbox) {
        this.reservas = Objects.requireNonNull(reservas);
        this.reloj = Objects.requireNonNull(reloj);
        this.outbox = Objects.requireNonNull(outbox);
    }

    @Override
    public int completar() {
        Instant ahora = reloj.ahora();
        List<Reserva> completadas = reservas.completarTerminadas(ahora);
        completadas.forEach(reserva -> outbox.registrar(EventoReserva.completada(reserva, ahora)));
        return completadas.size();
    }
}
