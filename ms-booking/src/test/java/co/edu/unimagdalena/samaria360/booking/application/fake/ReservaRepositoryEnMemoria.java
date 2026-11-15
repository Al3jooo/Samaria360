package co.edu.unimagdalena.samaria360.booking.application.fake;

import co.edu.unimagdalena.samaria360.booking.application.port.out.ClaveIdempotenciaRepetidaException;
import co.edu.unimagdalena.samaria360.booking.application.port.out.ReservaRepositoryPort;
import co.edu.unimagdalena.samaria360.booking.domain.model.CupoDescontado;
import co.edu.unimagdalena.samaria360.booking.domain.model.EstadoReserva;
import co.edu.unimagdalena.samaria360.booking.domain.model.FiltroReservas;
import co.edu.unimagdalena.samaria360.booking.domain.model.PaginaReservas;
import co.edu.unimagdalena.samaria360.booking.domain.model.Reserva;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** reservas_db en memoria, con la restricción única (turista, clave) de la base. */
public class ReservaRepositoryEnMemoria implements ReservaRepositoryPort {

    private final Map<UUID, Reserva> reservas = new LinkedHashMap<>();
    private final Map<UUID, List<CupoDescontado>> descuentos = new LinkedHashMap<>();
    private final Eventos eventos;
    private Runnable antesDeCancelar = () -> {
    };

    public ReservaRepositoryEnMemoria(Eventos eventos) {
        this.eventos = eventos;
    }

    /** Simula otra transacción que confirma entre la lectura de la reserva y su UPDATE condicional. */
    public void alCancelar(Runnable accion) {
        this.antesDeCancelar = accion;
    }

    public int cantidad() {
        return reservas.size();
    }

    public void guardar(Reserva reserva) {
        reservas.put(reserva.idReserva(), reserva);
    }

    @Override
    public void insertar(Reserva reserva) {
        eventos.registrar("reservas.insertar");
        boolean repetida = reservas.values().stream().anyMatch(r -> r.idTurista().equals(reserva.idTurista())
                && r.claveIdempotencia().equals(reserva.claveIdempotencia()));
        if (repetida) {
            throw new ClaveIdempotenciaRepetidaException(null);
        }
        reservas.put(reserva.idReserva(), reserva);
    }

    @Override
    public Optional<Reserva> buscarPorClave(UUID idTurista, UUID claveIdempotencia) {
        eventos.registrar("reservas.buscarPorClave");
        return reservas.values().stream().filter(r -> r.idTurista().equals(idTurista)
                && r.claveIdempotencia().equals(claveIdempotencia)).findFirst();
    }

    @Override
    public Optional<Reserva> buscarDeTurista(UUID idReserva, UUID idTurista) {
        return Optional.ofNullable(reservas.get(idReserva)).filter(r -> r.idTurista().equals(idTurista));
    }

    @Override
    public PaginaReservas listarDeTurista(UUID idTurista, FiltroReservas filtro, int pagina, int tamano) {
        return listar(r -> r.idTurista().equals(idTurista), filtro, pagina, tamano);
    }

    @Override
    public PaginaReservas listarDePrestador(UUID idPrestador, FiltroReservas filtro, int pagina, int tamano) {
        return listar(r -> r.idPrestador().equals(idPrestador), filtro, pagina, tamano);
    }

    private PaginaReservas listar(java.util.function.Predicate<Reserva> dueno, FiltroReservas filtro, int pagina,
                                  int tamano) {
        List<Reserva> propias = reservas.values().stream()
                .filter(r -> dueno.test(r) && filtro.incluye(r))
                .sorted(Comparator.comparing(Reserva::fechaInicio).thenComparing(Reserva::idReserva).reversed())
                .toList();
        int desde = Math.min(pagina * tamano, propias.size());
        return new PaginaReservas(propias.subList(desde, Math.min(desde + tamano, propias.size())), pagina, tamano,
                propias.size());
    }

    @Override
    public boolean cancelarSiConfirmada(UUID idReserva, UUID idTurista, Instant ahora) {
        antesDeCancelar.run();
        Reserva reserva = reservas.get(idReserva);
        if (reserva == null || !reserva.idTurista().equals(idTurista) || reserva.estado() != EstadoReserva.Confirmada) {
            return false;
        }
        reservas.put(idReserva, reserva.cancelada(ahora));
        return true;
    }

    @Override
    public List<Reserva> completarTerminadas(Instant ahora) {
        List<Reserva> cambiadas = new java.util.ArrayList<>();
        for (Reserva r : List.copyOf(reservas.values())) {
            if (r.estado() == EstadoReserva.Confirmada && !r.fechaFin().isAfter(ahora)) {
                Reserva completada = completada(r);
                reservas.put(r.idReserva(), completada);
                cambiadas.add(completada);
            }
        }
        return cambiadas;
    }

    /** Lo que hace el UPDATE de RF52 sobre una fila. */
    public static Reserva completada(Reserva r) {
        return new Reserva(r.idReserva(), r.idTurista(), r.idPrestador(), r.idServicio(), r.nombreServicio(),
                r.categoria(), r.idUnidad(), r.nombreUnidad(), EstadoReserva.Completada, r.cantidad(), r.personas(),
                r.precioUnitario(), r.unidadCobro(), r.montoTotal(), r.moneda(), r.fechaEntrada(), r.fechaSalida(),
                r.horaInicio(), r.fechaInicio(), r.fechaFin(), r.fechaCreacion(), null, r.claveIdempotencia(),
                r.hashSolicitud());
    }

    @Override
    public void registrarDescuentos(UUID idReserva, List<CupoDescontado> nuevos) {
        eventos.registrar("reservas.descuentos");
        descuentos.computeIfAbsent(idReserva, id -> new ArrayList<>()).addAll(nuevos);
    }

    @Override
    public List<CupoDescontado> descuentosDe(UUID idReserva) {
        return List.copyOf(descuentos.getOrDefault(idReserva, List.of()));
    }
}
