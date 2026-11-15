package co.edu.unimagdalena.samaria360.booking.application.service;

import co.edu.unimagdalena.samaria360.booking.application.fake.OutboxEventosEnMemoria;
import co.edu.unimagdalena.samaria360.booking.application.fake.CatalogoFalso;
import co.edu.unimagdalena.samaria360.booking.application.fake.Eventos;
import co.edu.unimagdalena.samaria360.booking.application.fake.InventarioEnMemoria;
import co.edu.unimagdalena.samaria360.booking.application.fake.OfertaMaterializadaEnMemoria;
import co.edu.unimagdalena.samaria360.booking.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.booking.application.fake.ReservaRepositoryEnMemoria;
import co.edu.unimagdalena.samaria360.booking.application.port.in.ComandoReserva;
import co.edu.unimagdalena.samaria360.booking.domain.model.CategoriaReservable;
import co.edu.unimagdalena.samaria360.booking.domain.model.DiaInventario;
import co.edu.unimagdalena.samaria360.booking.domain.model.EstadoReserva;
import co.edu.unimagdalena.samaria360.booking.domain.model.HorarioReservas;
import co.edu.unimagdalena.samaria360.booking.domain.model.Ofertas;
import co.edu.unimagdalena.samaria360.booking.domain.model.Reserva;
import co.edu.unimagdalena.samaria360.booking.domain.service.PoliticaReserva;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RF52 con dobles: el reloj decide qué reservas ya terminaron, y una segunda pasada no cambia nada. */
class CompletarReservasServiceTest {

    private static final UUID TURISTA = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final LocalDate DIA = Ofertas.HOY.plusDays(3);

    private final Eventos eventos = new Eventos();
    private final InventarioEnMemoria inventario = new InventarioEnMemoria(eventos)
            .con(new DiaInventario(Ofertas.TOUR, null, DIA, LocalTime.of(8, 0)), CategoriaReservable.Tour, 12, 12)
            .con(new DiaInventario(Ofertas.TOUR, null, DIA, LocalTime.of(14, 0)), CategoriaReservable.Tour, 12, 12);
    private final ReservaRepositoryEnMemoria reservas = new ReservaRepositoryEnMemoria(eventos);
    private final RelojFijo reloj = new RelojFijo(Ofertas.AHORA);
    private final CatalogoFalso catalogo = new CatalogoFalso(eventos).con(Ofertas.tour());
    private final OutboxEventosEnMemoria outbox = new OutboxEventosEnMemoria();
    private final ReservarServicioService reservar = new ReservarServicioService(reservas, inventario, catalogo,
            new MaterializarInventarioService(catalogo, inventario, new OfertaMaterializadaEnMemoria(eventos), reloj),
            reloj, new PoliticaReserva(HorarioReservas.POR_DEFECTO), outbox);
    private final CompletarReservasService completar = new CompletarReservasService(reservas, reloj, outbox);

    @Test
    void soloCompletaLasConfirmadasCuyaActividadYaTermino() {
        Reserva manana = reservar("08:00");
        Reserva tarde = reservar("14:00");
        Reserva cancelada = reservar("08:00");
        reservas.guardar(cancelada.cancelada(Ofertas.AHORA));

        reloj.fijar(manana.fechaFin()); // 12:00: terminó el de las 08:00, no el de las 14:00
        int primera = completar.completar();
        int segunda = completar.completar();

        assertThat(primera).isEqualTo(1);
        assertThat(segunda).isZero();
        assertThat(estado(manana)).isEqualTo(EstadoReserva.Completada);
        assertThat(estado(tarde)).isEqualTo(EstadoReserva.Confirmada);
        assertThat(estado(cancelada)).isEqualTo(EstadoReserva.Cancelada);

        reloj.fijar(tarde.fechaFin().minusSeconds(1));
        assertThat(completar.completar()).isZero();
        reloj.fijar(tarde.fechaFin());
        assertThat(completar.completar()).isEqualTo(1);
    }

    private Reserva reservar(String hora) {
        return reservar.reservar(new ComandoReserva(TURISTA, UUID.randomUUID(), Ofertas.TOUR, null, DIA.toString(),
                null, hora, null, 1, new BigDecimal("80000"))).reserva();
    }

    private EstadoReserva estado(Reserva reserva) {
        return reservas.buscarDeTurista(reserva.idReserva(), TURISTA).orElseThrow().estado();
    }
}
