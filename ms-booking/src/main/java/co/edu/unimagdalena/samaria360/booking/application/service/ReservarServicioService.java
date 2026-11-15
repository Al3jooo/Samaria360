package co.edu.unimagdalena.samaria360.booking.application.service;

import co.edu.unimagdalena.samaria360.booking.application.port.in.ComandoReserva;
import co.edu.unimagdalena.samaria360.booking.application.port.in.ReservarServicioUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.in.ResultadoReserva;
import co.edu.unimagdalena.samaria360.booking.application.port.out.CatalogoPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.ClaveIdempotenciaRepetidaException;
import co.edu.unimagdalena.samaria360.booking.application.port.out.InventarioPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.ReservaRepositoryPort;
import co.edu.unimagdalena.samaria360.booking.domain.exception.CupoAgotadoException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.FechaNoDisponibleException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.IdempotenciaConflictoException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.OfertaNoDisponibleException;
import co.edu.unimagdalena.samaria360.booking.domain.model.CupoDescontado;
import co.edu.unimagdalena.samaria360.booking.domain.model.DiaInventario;
import co.edu.unimagdalena.samaria360.booking.domain.model.EventoReserva;
import co.edu.unimagdalena.samaria360.booking.domain.model.FilaInventario;
import co.edu.unimagdalena.samaria360.booking.domain.model.OfertaReservable;
import co.edu.unimagdalena.samaria360.booking.domain.model.Reserva;
import co.edu.unimagdalena.samaria360.booking.domain.model.ReservaPlanificada;
import co.edu.unimagdalena.samaria360.booking.domain.model.SolicitudReserva;
import co.edu.unimagdalena.samaria360.booking.domain.service.PoliticaReserva;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reservar en dos fases (D3, D4, D6). El decorador transaccional de infraestructura las coordina:
 * <ol>
 *   <li>{@link #preparar}, <b>sin transacción</b>: valida, busca la clave de idempotencia, consulta el catálogo (HTTP),
 *   calcula el precio y materializa la oferta que ya leyó, sin otra llamada (DEC-152). Si algo falla aquí, no se tocó
 *   ninguna reserva ni ningún cupo;</li>
 *   <li>{@link #confirmar}, <b>en una transacción</b>: inserta la reserva (una clave repetida la revierte), comprueba que
 *   existan todas las filas de inventario, descuenta cada día en orden con un UPDATE condicional y registra lo
 *   descontado. Cualquier fallo revierte todo: no hay descuentos a medias;</li>
 *   <li>{@link #resolverRepetida}, si dos pedidos con la misma clave compitieron: la reserva ganadora, nunca un 500.</li>
 * </ol>
 * {@link #reservar} encadena las fases sin transacción, para las pruebas con dobles de los puertos.
 */
public class ReservarServicioService implements ReservarServicioUseCase {

    /** El pedido listo para confirmar, o la reserva que ya existía con esa clave y el mismo pedido. */
    public record Preparacion(ComandoReserva comando, String hash, ReservaPlanificada plan, Reserva repetida) {
    }

    private final ReservaRepositoryPort reservas;
    private final InventarioPort inventario;
    private final CatalogoPort catalogo;
    private final MaterializarInventarioService materializacion;
    private final RelojPort reloj;
    private final PoliticaReserva politica;

    private final OutboxEventosPort outbox;

    public ReservarServicioService(ReservaRepositoryPort reservas, InventarioPort inventario, CatalogoPort catalogo,
                                   MaterializarInventarioService materializacion, RelojPort reloj,
                                   PoliticaReserva politica, OutboxEventosPort outbox) {
        this.outbox = Objects.requireNonNull(outbox);
        this.reservas = Objects.requireNonNull(reservas);
        this.inventario = Objects.requireNonNull(inventario);
        this.catalogo = Objects.requireNonNull(catalogo);
        this.materializacion = Objects.requireNonNull(materializacion);
        this.reloj = Objects.requireNonNull(reloj);
        this.politica = Objects.requireNonNull(politica);
    }

    @Override
    public ResultadoReserva reservar(ComandoReserva comando) {
        Preparacion preparacion = preparar(comando);
        if (preparacion.repetida() != null) {
            return new ResultadoReserva(preparacion.repetida(), true);
        }
        try {
            return new ResultadoReserva(confirmar(preparacion), false);
        } catch (ClaveIdempotenciaRepetidaException carrera) {
            return resolverRepetida(preparacion);
        }
    }

    /** Fase 1, sin transacción. */
    public Preparacion preparar(ComandoReserva comando) {
        SolicitudReserva solicitud = SolicitudReserva.validar(comando.idServicio(), comando.idUnidad(),
                comando.fechaEntrada(), comando.fechaSalida(), comando.horaInicio(), comando.cantidad(),
                comando.personas(), comando.precioTotalEsperado());
        String hash = solicitud.hash();
        Optional<Reserva> previa = reservas.buscarPorClave(comando.idTurista(), comando.claveIdempotencia());
        if (previa.isPresent()) {
            return new Preparacion(comando, hash, null, mismaSolicitud(previa.get(), hash));
        }
        OfertaReservable oferta = catalogo.buscarReservable(solicitud.idServicio())
                .orElseThrow(() -> new OfertaNoDisponibleException(solicitud.idServicio()));
        ReservaPlanificada plan = politica.planificar(solicitud, oferta, reloj.ahora());
        materializacion.asegurar(oferta, plan.diasRequeridos().stream().map(DiaInventario::fecha)
                .max(Comparator.naturalOrder()).orElseThrow());
        return new Preparacion(comando, hash, plan, null);
    }

    /** Fase 2, dentro de la transacción que abre el decorador. */
    public Reserva confirmar(Preparacion preparacion) {
        ReservaPlanificada plan = preparacion.plan();
        ComandoReserva comando = preparacion.comando();
        Reserva reserva = Reserva.confirmar(plan, comando.idTurista(), comando.claveIdempotencia(),
                preparacion.hash(), reloj.ahora());
        reservas.insertar(reserva);

        List<FilaInventario> filas = inventario.buscarFilas(plan.diasRequeridos());
        if (filas.size() != plan.diasRequeridos().size()) {
            throw new FechaNoDisponibleException();
        }
        List<CupoDescontado> descuentos = new ArrayList<>();
        for (FilaInventario fila : filas) {
            if (!inventario.descontar(fila.idInventario(), plan.cantidad())) {
                throw new CupoAgotadoException();
            }
            descuentos.add(new CupoDescontado(fila.idInventario(), fila.dia().fecha(), fila.dia().hora(),
                    plan.cantidad()));
        }
        reservas.registrarDescuentos(reserva.idReserva(), descuentos);
        // ADR-011 (DEC-291): en la misma transacción; ms-analytics avisa al Prestador (RF30, RF63) y al Turista.
        outbox.registrar(EventoReserva.confirmada(reserva, reserva.fechaCreacion()));
        return reserva;
    }

    /** Fase 3, en una transacción nueva: la reserva que ganó con esta clave. */
    public ResultadoReserva resolverRepetida(Preparacion preparacion) {
        ComandoReserva comando = preparacion.comando();
        Reserva ganadora = reservas.buscarPorClave(comando.idTurista(), comando.claveIdempotencia())
                .orElseThrow(() -> new IllegalStateException("La clave repetida no tiene reserva"));
        return new ResultadoReserva(mismaSolicitud(ganadora, preparacion.hash()), true);
    }

    /** D6: la misma clave con otro pedido normalizado es un error del cliente, no una reserva nueva. */
    private static Reserva mismaSolicitud(Reserva previa, String hash) {
        if (!previa.hashSolicitud().equals(hash)) {
            throw new IdempotenciaConflictoException();
        }
        return previa;
    }
}
