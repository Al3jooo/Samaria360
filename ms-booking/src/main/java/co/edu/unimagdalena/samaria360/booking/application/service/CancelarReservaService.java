package co.edu.unimagdalena.samaria360.booking.application.service;

import co.edu.unimagdalena.samaria360.booking.application.port.in.CancelarReservaUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.out.InventarioPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.booking.domain.model.EventoReserva;
import co.edu.unimagdalena.samaria360.booking.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.ReservaRepositoryPort;
import co.edu.unimagdalena.samaria360.booking.domain.exception.ReservaNoCancelableException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.ReservaNoEncontradaException;
import co.edu.unimagdalena.samaria360.booking.domain.model.CupoDescontado;
import co.edu.unimagdalena.samaria360.booking.domain.model.EstadoReserva;
import co.edu.unimagdalena.samaria360.booking.domain.model.Reserva;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cancelar (RF18, RF44). Va entero en una transacción (decorador de infraestructura):
 * <ul>
 *   <li>la reserva de otro turista no existe (404);</li>
 *   <li>una ya Cancelada se devuelve igual, sin liberar nada (idempotente, D6);</li>
 *   <li>el cambio de estado es un UPDATE condicional: si dos cancelaciones compiten, solo una libera los cupos; si
 *   RF52 completó la reserva entre la lectura y el UPDATE, responde {@code RESERVA_NO_CANCELABLE};</li>
 *   <li>los cupos vuelven fila por fila, en el orden de fecha con que se descontaron (D3).</li>
 * </ul>
 */
public class CancelarReservaService implements CancelarReservaUseCase {

    private final ReservaRepositoryPort reservas;
    private final InventarioPort inventario;
    private final RelojPort reloj;

    private final OutboxEventosPort outbox;

    public CancelarReservaService(ReservaRepositoryPort reservas, InventarioPort inventario, RelojPort reloj,
                                  OutboxEventosPort outbox) {
        this.outbox = Objects.requireNonNull(outbox);
        this.reservas = Objects.requireNonNull(reservas);
        this.inventario = Objects.requireNonNull(inventario);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public Reserva cancelar(UUID idReserva, UUID idTurista) {
        Reserva reserva = buscar(idReserva, idTurista);
        if (reserva.estado() == EstadoReserva.Cancelada) {
            return reserva;
        }
        Instant ahora = reloj.ahora();
        reserva.verificarCancelable(ahora);
        if (!reservas.cancelarSiConfirmada(idReserva, idTurista, ahora)) {
            // Otra transacción cambió el estado primero: otra cancelación (ya liberó los cupos) o RF52 (Completada).
            Reserva actual = buscar(idReserva, idTurista);
            if (actual.estado() == EstadoReserva.Cancelada) {
                return actual;
            }
            throw new ReservaNoCancelableException();
        }
        for (CupoDescontado descuento : reservas.descuentosDe(idReserva)) {
            inventario.liberar(descuento.idInventario(), descuento.cantidad());
        }
        Reserva cancelada = reserva.cancelada(ahora);
        // ADR-011 (DEC-291): auditoría de RF13 y aviso al Turista (RF63) en ms-analytics. Solo la cancelación que
        // cambió el estado: la repetida no publica nada.
        outbox.registrar(EventoReserva.cancelada(cancelada, idTurista, ahora));
        return cancelada;
    }

    private Reserva buscar(UUID idReserva, UUID idTurista) {
        return reservas.buscarDeTurista(idReserva, idTurista).orElseThrow(ReservaNoEncontradaException::new);
    }
}
