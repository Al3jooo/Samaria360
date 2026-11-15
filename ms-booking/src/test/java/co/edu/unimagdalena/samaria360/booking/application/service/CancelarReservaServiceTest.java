package co.edu.unimagdalena.samaria360.booking.application.service;

import co.edu.unimagdalena.samaria360.booking.application.fake.OutboxEventosEnMemoria;
import co.edu.unimagdalena.samaria360.booking.application.fake.CatalogoFalso;
import co.edu.unimagdalena.samaria360.booking.application.fake.Eventos;
import co.edu.unimagdalena.samaria360.booking.application.fake.InventarioEnMemoria;
import co.edu.unimagdalena.samaria360.booking.application.fake.OfertaMaterializadaEnMemoria;
import co.edu.unimagdalena.samaria360.booking.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.booking.application.fake.ReservaRepositoryEnMemoria;
import co.edu.unimagdalena.samaria360.booking.application.port.in.ComandoReserva;
import co.edu.unimagdalena.samaria360.booking.domain.exception.CancelacionFueraDePlazoException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.ReservaNoCancelableException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.ReservaNoEncontradaException;
import co.edu.unimagdalena.samaria360.booking.domain.model.CategoriaReservable;
import co.edu.unimagdalena.samaria360.booking.domain.model.DiaInventario;
import co.edu.unimagdalena.samaria360.booking.domain.model.EstadoReserva;
import co.edu.unimagdalena.samaria360.booking.domain.model.EventoReserva;
import co.edu.unimagdalena.samaria360.booking.domain.model.HorarioReservas;
import co.edu.unimagdalena.samaria360.booking.domain.model.Ofertas;
import co.edu.unimagdalena.samaria360.booking.domain.model.Reserva;
import co.edu.unimagdalena.samaria360.booking.domain.service.PoliticaReserva;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF18 y RF44 con dobles: libera lo descontado una sola vez y respeta el plazo de 24 horas. */
class CancelarReservaServiceTest {

    private static final UUID TURISTA = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final LocalDate DIA = Ofertas.HOY.plusDays(3);
    private static final DiaInventario OCHO = new DiaInventario(Ofertas.TOUR, null, DIA, LocalTime.of(8, 0));

    private final Eventos eventos = new Eventos();
    private final InventarioEnMemoria inventario = new InventarioEnMemoria(eventos)
            .con(OCHO, CategoriaReservable.Tour, 12, 12);
    private final ReservaRepositoryEnMemoria reservas = new ReservaRepositoryEnMemoria(eventos);
    private final RelojFijo reloj = new RelojFijo(Ofertas.AHORA);
    private final OutboxEventosEnMemoria outbox = new OutboxEventosEnMemoria();
    private final CancelarReservaService cancelar = new CancelarReservaService(reservas, inventario, reloj, outbox);
    private Reserva reserva;

    @BeforeEach
    void reservarTresPersonas() {
        CatalogoFalso catalogo = new CatalogoFalso(eventos).con(Ofertas.tour());
        ReservarServicioService reservar = new ReservarServicioService(reservas, inventario, catalogo,
                new MaterializarInventarioService(catalogo, inventario, new OfertaMaterializadaEnMemoria(eventos), reloj),
                reloj, new PoliticaReserva(HorarioReservas.POR_DEFECTO), outbox);
        reserva = reservar.reservar(new ComandoReserva(TURISTA, UUID.randomUUID(), Ofertas.TOUR, null,
                DIA.toString(), null, "08:00", null, 3, new BigDecimal("240000"))).reserva();
        assertThat(inventario.disponibles(OCHO)).isEqualTo(9);
    }

    @Test
    void cancelarDevuelveLosCupos() {
        Reserva cancelada = cancelar.cancelar(reserva.idReserva(), TURISTA);

        assertThat(cancelada.estado()).isEqualTo(EstadoReserva.Cancelada);
        assertThat(inventario.disponibles(OCHO)).isEqualTo(12);
    }

    /** ADR-011 (DEC-291): la reserva publica su confirmación y su cancelación, con quién canceló (RF13). */
    @Test
    void reservarYCancelarEncolanSusEventos() {
        cancelar.cancelar(reserva.idReserva(), TURISTA);

        assertThat(outbox.eventos()).extracting(e -> e.tipo())
                .containsExactly(EventoReserva.Tipo.ReservaConfirmada, EventoReserva.Tipo.ReservaCancelada);
        EventoReserva cancelacion = outbox.eventos().get(1);
        assertThat(cancelacion.datos()).containsEntry("canceladaPor", TURISTA.toString())
                .containsEntry("idReserva", reserva.idReserva().toString()).containsEntry("personas", 3);
    }

    /** La cancelación repetida responde lo mismo y no publica otro evento. */
    @Test
    void cancelarDosVecesPublicaUnaSolaCancelacion() {
        cancelar.cancelar(reserva.idReserva(), TURISTA);
        cancelar.cancelar(reserva.idReserva(), TURISTA);

        assertThat(outbox.eventos()).filteredOn(e -> e.tipo() == EventoReserva.Tipo.ReservaCancelada).hasSize(1);
    }

    @Test
    void cancelarDosVecesNoLiberaDosVeces() {
        cancelar.cancelar(reserva.idReserva(), TURISTA);

        Reserva otraVez = cancelar.cancelar(reserva.idReserva(), TURISTA);

        assertThat(otraVez.estado()).isEqualTo(EstadoReserva.Cancelada);
        assertThat(inventario.disponibles(OCHO)).isEqualTo(12);
        assertThat(eventos.todos().stream().filter("inventario.liberar"::equals)).hasSize(1);
    }

    @Test
    void fueraDelPlazoNoSeCancelaNiSeLiberaNada() {
        reloj.fijar(reserva.cancelableHasta().plusSeconds(1));

        assertThatThrownBy(() -> cancelar.cancelar(reserva.idReserva(), TURISTA))
                .isInstanceOf(CancelacionFueraDePlazoException.class);
        assertThat(inventario.disponibles(OCHO)).isEqualTo(9);
        assertThat(reservas.buscarDeTurista(reserva.idReserva(), TURISTA).orElseThrow().estado())
                .isEqualTo(EstadoReserva.Confirmada);
    }

    @Test
    void conExactamente24HorasSiSeCancela() {
        reloj.fijar(reserva.cancelableHasta());

        assertThat(cancelar.cancelar(reserva.idReserva(), TURISTA).estado()).isEqualTo(EstadoReserva.Cancelada);
    }

    @Test
    void siRf52LaCompletaAntesDelUpdateLaCancelacionRespondeNoCancelableYNoLibera() {
        reservas.alCancelar(() -> reservas.guardar(ReservaRepositoryEnMemoria.completada(reserva)));

        assertThatThrownBy(() -> cancelar.cancelar(reserva.idReserva(), TURISTA))
                .isInstanceOf(ReservaNoCancelableException.class);
        assertThat(inventario.disponibles(OCHO)).isEqualTo(9);
    }

    @Test
    void unaReservaCompletadaNoSeCancela() {
        reservas.guardar(ReservaRepositoryEnMemoria.completada(reserva));

        assertThatThrownBy(() -> cancelar.cancelar(reserva.idReserva(), TURISTA))
                .isInstanceOf(ReservaNoCancelableException.class);
        assertThat(inventario.disponibles(OCHO)).isEqualTo(9);
    }

    @Test
    void laReservaDeOtroTuristaNoExiste() {
        assertThatThrownBy(() -> cancelar.cancelar(reserva.idReserva(), UUID.randomUUID()))
                .isInstanceOf(ReservaNoEncontradaException.class);
        assertThatThrownBy(() -> cancelar.cancelar(UUID.randomUUID(), TURISTA))
                .isInstanceOf(ReservaNoEncontradaException.class);
    }
}
