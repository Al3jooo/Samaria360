package co.edu.unimagdalena.samaria360.booking.application.port.out;

import co.edu.unimagdalena.samaria360.booking.domain.model.CupoDescontado;
import co.edu.unimagdalena.samaria360.booking.domain.model.FiltroReservas;
import co.edu.unimagdalena.samaria360.booking.domain.model.PaginaReservas;
import co.edu.unimagdalena.samaria360.booking.domain.model.Reserva;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reservas de reservas_db. */
public interface ReservaRepositoryPort {

    /** @throws ClaveIdempotenciaRepetidaException si el turista ya usó esa clave (detectado al insertar) */
    void insertar(Reserva reserva);

    Optional<Reserva> buscarPorClave(UUID idTurista, UUID claveIdempotencia);

    /** Vacío si no existe <b>o es de otro turista</b>. */
    Optional<Reserva> buscarDeTurista(UUID idReserva, UUID idTurista);

    /** Las que cumplen el filtro, por fecha de inicio de la actividad descendente (desempate por id). */
    PaginaReservas listarDeTurista(UUID idTurista, FiltroReservas filtro, int pagina, int tamano);

    /** Como {@link #listarDeTurista}, con las reservas de los servicios del prestador (RF12, DEC-191). */
    PaginaReservas listarDePrestador(UUID idPrestador, FiltroReservas filtro, int pagina, int tamano);

    /** UPDATE condicional (solo si sigue Confirmada). @return false si otra cancelación ganó o ya no está Confirmada */
    boolean cancelarSiConfirmada(UUID idReserva, UUID idTurista, Instant ahora);

    /**
     * RF52: UPDATE condicional {@code estado = 'Confirmada' AND fecha_fin <= ahora} → Completada. Idempotente.
     *
     * @return las reservas que cambiaron, ya Completadas (para sus eventos, ADR-011)
     */
    List<Reserva> completarTerminadas(Instant ahora);

    void registrarDescuentos(UUID idReserva, List<CupoDescontado> descuentos);

    /** En el orden de fecha y hora con que se descontaron. */
    List<CupoDescontado> descuentosDe(UUID idReserva);
}
