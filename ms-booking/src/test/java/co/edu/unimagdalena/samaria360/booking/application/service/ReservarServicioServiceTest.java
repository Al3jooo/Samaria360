package co.edu.unimagdalena.samaria360.booking.application.service;

import co.edu.unimagdalena.samaria360.booking.application.fake.OutboxEventosEnMemoria;
import co.edu.unimagdalena.samaria360.booking.application.fake.CatalogoFalso;
import co.edu.unimagdalena.samaria360.booking.application.fake.Eventos;
import co.edu.unimagdalena.samaria360.booking.application.fake.InventarioEnMemoria;
import co.edu.unimagdalena.samaria360.booking.application.fake.OfertaMaterializadaEnMemoria;
import co.edu.unimagdalena.samaria360.booking.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.booking.application.fake.ReservaRepositoryEnMemoria;
import co.edu.unimagdalena.samaria360.booking.application.port.in.ComandoReserva;
import co.edu.unimagdalena.samaria360.booking.application.port.in.ResultadoReserva;
import co.edu.unimagdalena.samaria360.booking.domain.exception.CatalogoNoDisponibleException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.CupoAgotadoException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.DatosReservaInvalidosException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.FechaNoDisponibleException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.IdempotenciaConflictoException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.OfertaNoDisponibleException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.PrecioCambiadoException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.UnidadNoDisponibleException;
import co.edu.unimagdalena.samaria360.booking.domain.model.CategoriaReservable;
import co.edu.unimagdalena.samaria360.booking.domain.model.DiaInventario;
import co.edu.unimagdalena.samaria360.booking.domain.model.HorarioReservas;
import co.edu.unimagdalena.samaria360.booking.domain.model.MotivoInvalido;
import co.edu.unimagdalena.samaria360.booking.domain.model.Ofertas;
import co.edu.unimagdalena.samaria360.booking.domain.service.PoliticaReserva;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.UUID;

import static co.edu.unimagdalena.samaria360.booking.domain.model.Ofertas.DOBLE;
import static co.edu.unimagdalena.samaria360.booking.domain.model.Ofertas.HOTEL;
import static co.edu.unimagdalena.samaria360.booking.domain.model.Ofertas.SUITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Reservar con dobles de los puertos: orden de las fases, idempotencia, errores que no tocan la base y la
 * materialización de la oferta ya leída, sin otra llamada al catálogo (DEC-152).
 */
class ReservarServicioServiceTest {

    private static final UUID TURISTA = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final LocalDate ENTRADA = Ofertas.HOY.plusDays(10);

    private final Eventos eventos = new Eventos();
    private final CatalogoFalso catalogo = new CatalogoFalso(eventos).con(Ofertas.hotel(), Ofertas.tour());
    private final InventarioEnMemoria inventario = new InventarioEnMemoria(eventos);
    private final ReservaRepositoryEnMemoria reservas = new ReservaRepositoryEnMemoria(eventos);
    private final OfertaMaterializadaEnMemoria marcas = new OfertaMaterializadaEnMemoria(eventos);
    private final RelojFijo reloj = new RelojFijo(Ofertas.AHORA);
    private final OutboxEventosEnMemoria outbox = new OutboxEventosEnMemoria();
    private final ReservarServicioService servicio = new ReservarServicioService(reservas, inventario, catalogo,
            new MaterializarInventarioService(catalogo, inventario, marcas, reloj), reloj,
            new PoliticaReserva(HorarioReservas.POR_DEFECTO), outbox);

    @BeforeEach
    void inventarioDeDosNochesPorUnidad() {
        for (String unidad : new String[]{DOBLE, SUITE}) {
            for (int noche = 0; noche < 2; noche++) {
                inventario.con(noche(unidad, noche), CategoriaReservable.Alojamiento, unidad.equals(DOBLE) ? 12 : 3,
                        unidad.equals(DOBLE) ? 12 : 3);
            }
        }
    }

    @Test
    void confirmaDescontandoCadaNocheDeLaUnidadElegida() {
        ResultadoReserva resultado = servicio.reservar(suite(UUID.randomUUID(), 2, "1040000"));

        assertThat(resultado.repetida()).isFalse();
        assertThat(resultado.reserva().nombreUnidad()).isEqualTo("Suite familiar");
        assertThat(inventario.disponibles(noche(SUITE, 0))).isEqualTo(1);
        assertThat(inventario.disponibles(noche(SUITE, 1))).isEqualTo(1);
        assertThat(inventario.disponibles(noche(DOBLE, 0))).isEqualTo(12);
        assertThat(reservas.descuentosDe(resultado.reserva().idReserva())).hasSize(2);
    }

    @Test
    void elCatalogoSeConsultaAntesDeTocarLaBaseYLaReservaSeInsertaAntesQueElInventario() {
        servicio.reservar(suite(UUID.randomUUID(), 1, "520000"));

        // La materialización (DEC-152) va después del catálogo y antes de la transacción que inserta la reserva.
        assertThat(eventos.todos()).containsExactly("reservas.buscarPorClave", "catalogo.detalle",
                "marcas.consultar", "marcas.registrar", "reservas.insertar", "inventario.buscar", "inventario.descontar", "inventario.descontar",
                "reservas.descuentos");
    }

    @Test
    void conElPrecioCambiadoNoSeInsertaNiSeDescuentaNada() {
        assertThatThrownBy(() -> servicio.reservar(suite(UUID.randomUUID(), 1, "500000")))
                .isInstanceOf(PrecioCambiadoException.class);

        assertThat(reservas.cantidad()).isZero();
        assertThat(eventos.todos()).doesNotContain("reservas.insertar", "inventario.descontar");
    }

    @Test
    void unServicioQueElCatalogoNoMuestraNoEsReservable() {
        ComandoReserva otroServicio = new ComandoReserva(TURISTA, UUID.randomUUID(), "6a00000000000000000000ff", null,
                ENTRADA.toString(), null, "08:00", null, 1, new BigDecimal("1"));

        assertThatThrownBy(() -> servicio.reservar(otroServicio)).isInstanceOf(OfertaNoDisponibleException.class);
        assertThat(reservas.cantidad()).isZero();
    }

    @Test
    void unaUnidadInexistenteSeRechazaSinAbrirLaTransaccion() {
        ComandoReserva comando = new ComandoReserva(TURISTA, UUID.randomUUID(), HOTEL, "6b00000000000000000000ff",
                ENTRADA.toString(), ENTRADA.plusDays(1).toString(), null, 1, 1, new BigDecimal("150000"));

        assertThatThrownBy(() -> servicio.reservar(comando)).isInstanceOf(UnidadNoDisponibleException.class);
        assertThat(eventos.todos()).doesNotContain("reservas.insertar", "inventario.buscar");
    }

    @Test
    void conElCatalogoCaidoNoSeTocaLaBase() {
        catalogo.caer();

        assertThatThrownBy(() -> servicio.reservar(suite(UUID.randomUUID(), 1, "520000")))
                .isInstanceOf(CatalogoNoDisponibleException.class);
        assertThat(reservas.cantidad()).isZero();
    }

    @Test
    void laMismaClaveConElMismoPedidoDevuelveLaMismaReservaSinConsultarElCatalogo() {
        UUID clave = UUID.randomUUID();
        ResultadoReserva primera = servicio.reservar(suite(clave, 1, "520000"));

        ResultadoReserva segunda = servicio.reservar(suite(clave, 1, "520000.00"));

        assertThat(segunda.repetida()).isTrue();
        assertThat(segunda.reserva().idReserva()).isEqualTo(primera.reserva().idReserva());
        assertThat(catalogo.consultasDetalle()).isEqualTo(1);
        assertThat(inventario.disponibles(noche(SUITE, 0))).isEqualTo(2);
    }

    @Test
    void laMismaClaveConOtraUnidadEsUnConflicto() {
        UUID clave = UUID.randomUUID();
        servicio.reservar(suite(clave, 1, "520000"));
        ComandoReserva doble = new ComandoReserva(TURISTA, clave, HOTEL, DOBLE, ENTRADA.toString(),
                ENTRADA.plusDays(2).toString(), null, 1, 1, new BigDecimal("300000"));

        assertThatThrownBy(() -> servicio.reservar(doble)).isInstanceOf(IdempotenciaConflictoException.class);
        assertThat(reservas.cantidad()).isEqualTo(1);
    }

    @Test
    void siFaltaUnaNocheEnElInventarioLaFechaNoEstaDisponible() {
        ComandoReserva tresNoches = new ComandoReserva(TURISTA, UUID.randomUUID(), HOTEL, SUITE, ENTRADA.toString(),
                ENTRADA.plusDays(3).toString(), null, 1, 1, new BigDecimal("780000"));

        assertThatThrownBy(() -> servicio.reservar(tresNoches)).isInstanceOf(FechaNoDisponibleException.class);
        assertThat(eventos.todos()).doesNotContain("inventario.descontar");
    }

    @Test
    void siUnaNocheNoAlcanzaElCupoEstaAgotado() {
        servicio.reservar(suite(UUID.randomUUID(), 3, "1560000"));

        assertThatThrownBy(() -> servicio.reservar(suite(UUID.randomUUID(), 1, "520000")))
                .isInstanceOf(CupoAgotadoException.class);
    }

    // ------------------------------------------------------------------ materialización (DEC-152)

    @Test
    void materializaLaOfertaYaLeidaSinOtraLlamadaAlCatalogoYAntesDeInsertar() {
        catalogo.con(Ofertas.tourQueSale(Ofertas.TODOS_LOS_DIAS, LocalTime.of(8, 0), LocalTime.of(14, 0)));

        servicio.reservar(tour(ENTRADA, "08:00", 2));

        assertThat(catalogo.consultasDetalle()).isEqualTo(1);
        assertThat(eventos.todos()).containsSubsequence("catalogo.detalle", "inventario.asegurar", "marcas.registrar",
                "reservas.insertar");
        assertThat(inventario.disponibles(new DiaInventario(Ofertas.TOUR, null, ENTRADA, LocalTime.of(8, 0))))
                .isEqualTo(10);
        assertThat(inventario.totales(new DiaInventario(Ofertas.TOUR, null, Ofertas.HOY.plusDays(59),
                LocalTime.of(14, 0)))).isEqualTo(12);
        assertThat(marcas.marca(Ofertas.TOUR)).isEqualTo(Ofertas.HOY.plusDays(59));
    }

    @Test
    void unDiaQueElTourNoOperaNoTieneInventario() {
        // ENTRADA es miércoles; el tour solo sale sábados y domingos.
        catalogo.con(Ofertas.tourQueSale(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), LocalTime.of(8, 0)));

        assertThatThrownBy(() -> servicio.reservar(tour(ENTRADA, "08:00", 1)))
                .isInstanceOf(FechaNoDisponibleException.class);
        assertThat(servicio.reservar(tour(LocalDate.of(2030, 3, 23), "08:00", 1)).reserva().fechaEntrada())
                .isEqualTo(LocalDate.of(2030, 3, 23));
    }

    @Test
    void unaSalidaQueElTourNoOfreceNoTieneInventario() {
        catalogo.con(Ofertas.tourQueSale(Ofertas.TODOS_LOS_DIAS, LocalTime.of(8, 0)));

        assertThatThrownBy(() -> servicio.reservar(tour(ENTRADA, "14:00", 1)))
                .isInstanceOf(FechaNoDisponibleException.class);
    }

    @Test
    void conLaMarcaAlDiaNoSeVuelveAMaterializar() {
        catalogo.con(Ofertas.tourQueSale(Ofertas.TODOS_LOS_DIAS, LocalTime.of(8, 0)));
        servicio.reservar(tour(ENTRADA, "08:00", 1));

        servicio.reservar(tour(ENTRADA.plusDays(1), "08:00", 1));

        assertThat(eventos.todos().stream().filter("inventario.asegurar"::equals)).hasSize(1);
    }

    @Test
    void unaFechaFueraDelHorizonteNuncaSeMaterializa() {
        catalogo.con(Ofertas.tourQueSale(Ofertas.TODOS_LOS_DIAS, LocalTime.of(8, 0)));

        assertThatThrownBy(() -> servicio.reservar(tour(Ofertas.HOY.plusDays(60), "08:00", 1)))
                .isInstanceOf(FechaNoDisponibleException.class);
        assertThat(inventario.dias()).noneMatch(dia -> dia.fecha().isAfter(Ofertas.HOY.plusDays(59)));
    }

    @Test
    void gastronomiaEsNoReservableSinLlamarAlMaterializador() {
        catalogo.con(Ofertas.restaurante());
        ComandoReserva restaurante = new ComandoReserva(TURISTA, UUID.randomUUID(), Ofertas.RESTAURANTE, null,
                ENTRADA.toString(), null, "12:00", null, 2, new BigDecimal("100000"));

        DatosReservaInvalidosException error = catchThrowableOfType(DatosReservaInvalidosException.class,
                () -> servicio.reservar(restaurante));

        assertThat(error.getCampos()).containsEntry("idServicio", MotivoInvalido.CATEGORIA_NO_RESERVABLE);
        assertThat(eventos.todos()).doesNotContain("marcas.consultar", "inventario.asegurar", "marcas.registrar");
    }

    private static ComandoReserva tour(LocalDate fecha, String hora, int personas) {
        return new ComandoReserva(TURISTA, UUID.randomUUID(), Ofertas.TOUR, null, fecha.toString(), null, hora, null,
                personas, new BigDecimal(80000L * personas));
    }

    private static DiaInventario noche(String unidad, int noche) {
        return new DiaInventario(HOTEL, unidad, ENTRADA.plusDays(noche), null);
    }

    /** Dos noches de la Suite (260.000 por noche y habitación). */
    private static ComandoReserva suite(UUID clave, int habitaciones, String total) {
        return new ComandoReserva(TURISTA, clave, HOTEL, SUITE, ENTRADA.toString(), ENTRADA.plusDays(2).toString(),
                null, habitaciones, 2, new BigDecimal(total));
    }
}
