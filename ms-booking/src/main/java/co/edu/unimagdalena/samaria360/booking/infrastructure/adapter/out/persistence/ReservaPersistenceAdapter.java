package co.edu.unimagdalena.samaria360.booking.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.booking.application.port.out.ClaveIdempotenciaRepetidaException;
import co.edu.unimagdalena.samaria360.booking.application.port.out.ReservaRepositoryPort;
import co.edu.unimagdalena.samaria360.booking.domain.model.CupoDescontado;
import co.edu.unimagdalena.samaria360.booking.domain.model.EstadoReserva;
import co.edu.unimagdalena.samaria360.booking.domain.model.FiltroReservas;
import co.edu.unimagdalena.samaria360.booking.domain.model.PaginaReservas;
import co.edu.unimagdalena.samaria360.booking.domain.model.Reserva;
import co.edu.unimagdalena.samaria360.booking.infrastructure.adapter.out.persistence.entity.ReservaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Time;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reservas con JPA (entidad validada contra el esquema) y lo descontado ({@code reserva_cupo_dia}) con SQL nativo, en
 * la misma transacción y la misma conexión.
 */
@Component
public class ReservaPersistenceAdapter implements ReservaRepositoryPort {

    private final ReservaJpaRepository repositorio;
    private final JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    public ReservaPersistenceAdapter(ReservaJpaRepository repositorio, JdbcTemplate jdbc) {
        this.repositorio = repositorio;
        this.jdbc = jdbc;
    }

    /**
     * {@code persist} + {@code flush}: la clave de idempotencia repetida se detecta aquí, dentro de la transacción y
     * antes de tocar el inventario, y no al confirmar (D6).
     */
    @Override
    public void insertar(Reserva reserva) {
        try {
            entityManager.persist(aEntidad(reserva));
            entityManager.flush();
        } catch (RuntimeException error) {
            if (RestriccionesUnicas.violada(error).filter(RestriccionesUnicas.CLAVE_IDEMPOTENCIA::equals).isPresent()) {
                throw new ClaveIdempotenciaRepetidaException(error);
            }
            throw error;
        }
    }

    @Override
    public Optional<Reserva> buscarPorClave(UUID idTurista, UUID claveIdempotencia) {
        return repositorio.findByIdTuristaAndClaveIdempotencia(idTurista, claveIdempotencia)
                .map(ReservaPersistenceAdapter::aDominio);
    }

    @Override
    public Optional<Reserva> buscarDeTurista(UUID idReserva, UUID idTurista) {
        return repositorio.findByIdReservaAndIdTurista(idReserva, idTurista).map(ReservaPersistenceAdapter::aDominio);
    }

    /**
     * La regla del cruce de {@link FiltroReservas}: {@code fecha_fin > desde 00:00} y {@code fecha_inicio < (hasta + 1)
     * 00:00}, en Santa Marta. Orden por inicio descendente con desempate por id: la paginación es estable aunque dos
     * reservas empiecen a la misma hora (índice de V2).
     */
    @Override
    public PaginaReservas listarDeTurista(UUID idTurista, FiltroReservas filtro, int pagina, int tamano) {
        return listar("idTurista", idTurista, filtro, pagina, tamano);
    }

    /** La misma consulta sobre {@code id_prestador} (índice de V6, DEC-191). */
    @Override
    public PaginaReservas listarDePrestador(UUID idPrestador, FiltroReservas filtro, int pagina, int tamano) {
        return listar("idPrestador", idPrestador, filtro, pagina, tamano);
    }

    private PaginaReservas listar(String dueno, UUID id, FiltroReservas filtro, int pagina, int tamano) {
        Specification<ReservaEntity> condiciones = (raiz, consulta, cb) -> {
            List<Predicate> partes = new ArrayList<>();
            partes.add(cb.equal(raiz.get(dueno), id));
            if (filtro.idServicio() != null) {
                partes.add(cb.equal(raiz.get("idServicioMongo"), filtro.idServicio()));
            }
            if (filtro.terminaDespuesDe() != null) {
                partes.add(cb.greaterThan(raiz.get("fechaFin"), filtro.terminaDespuesDe()));
            }
            if (filtro.empiezaAntesDe() != null) {
                partes.add(cb.lessThan(raiz.get("fechaInicio"), filtro.empiezaAntesDe()));
            }
            if (!filtro.estados().isEmpty()) {
                partes.add(raiz.get("estado").in(filtro.estados()));
            }
            return cb.and(partes.toArray(Predicate[]::new));
        };
        Sort orden = Sort.by(Sort.Order.desc("fechaInicio"), Sort.Order.desc("idReserva"));
        Page<ReservaEntity> resultado = repositorio.findAll(condiciones, PageRequest.of(pagina, tamano, orden));
        return new PaginaReservas(resultado.getContent().stream().map(ReservaPersistenceAdapter::aDominio).toList(),
                pagina, tamano, resultado.getTotalElements());
    }

    @Override
    public List<Reserva> completarTerminadas(Instant ahora) {
        // Un solo UPDATE condicional e idempotente (RF52), que devuelve las que cambiaron para sus eventos (ADR-011).
        List<UUID> ids = jdbc.queryForList("""
                UPDATE reserva SET estado = 'Completada'
                WHERE estado = 'Confirmada' AND fecha_fin <= ?
                RETURNING id_reserva
                """, UUID.class, java.sql.Timestamp.from(ahora));
        return ids.stream()
                .map(id -> repositorio.findById(id).map(ReservaPersistenceAdapter::aDominio).orElseThrow())
                .toList();
    }

    @Override
    public boolean cancelarSiConfirmada(UUID idReserva, UUID idTurista, Instant ahora) {
        return repositorio.cancelarSiConfirmada(idReserva, idTurista, ahora, EstadoReserva.Cancelada,
                EstadoReserva.Confirmada) == 1;
    }

    @Override
    public void registrarDescuentos(UUID idReserva, List<CupoDescontado> descuentos) {
        jdbc.batchUpdate("INSERT INTO reserva_cupo_dia (id_reserva, id_inventario, cantidad_descontada) VALUES (?, ?, ?)",
                descuentos, descuentos.size(), (sentencia, descuento) -> {
                    sentencia.setObject(1, idReserva);
                    sentencia.setLong(2, descuento.idInventario());
                    sentencia.setInt(3, descuento.cantidad());
                });
    }

    @Override
    public List<CupoDescontado> descuentosDe(UUID idReserva) {
        return jdbc.query("""
                        SELECT d.id_inventario, i.fecha, i.hora_inicio, d.cantidad_descontada
                        FROM reserva_cupo_dia d JOIN inventario_cupo i ON i.id_inventario = d.id_inventario
                        WHERE d.id_reserva = ?
                        ORDER BY i.fecha, i.hora_inicio NULLS FIRST""",
                (fila, n) -> {
                    Time hora = fila.getTime("hora_inicio");
                    return new CupoDescontado(fila.getLong("id_inventario"), fila.getDate("fecha").toLocalDate(),
                            hora == null ? null : hora.toLocalTime(), fila.getInt("cantidad_descontada"));
                },
                idReserva);
    }

    private static ReservaEntity aEntidad(Reserva r) {
        return new ReservaEntity(r.idReserva(), r.idTurista(), r.idPrestador(), r.idServicio(), r.nombreServicio(),
                r.categoria(), r.idUnidad(), r.nombreUnidad(), r.estado(), r.cantidad(), r.personas(),
                r.precioUnitario(), r.unidadCobro(), r.montoTotal(), r.moneda(), r.fechaEntrada(), r.fechaSalida(),
                r.horaInicio(), r.fechaInicio(), r.fechaFin(), r.fechaCreacion(), r.fechaCancelacion(),
                r.claveIdempotencia(), r.hashSolicitud());
    }

    private static Reserva aDominio(ReservaEntity e) {
        return new Reserva(e.getIdReserva(), e.getIdTurista(), e.getIdPrestador(), e.getIdServicioMongo(),
                e.getNombreServicio(), e.getCategoria(), e.getIdUnidad(), e.getNombreUnidad(), e.getEstado(),
                e.getCantidadSolicitada(), e.getPersonas(), e.getPrecioUnitario(), e.getUnidadCobro(),
                e.getMontoTotal(), e.getMoneda(), e.getFechaEntrada(), e.getFechaSalida(), e.getHoraInicio(),
                e.getFechaInicio(), e.getFechaFin(), e.getFechaCreacion(), e.getFechaCancelacion(),
                e.getClaveIdempotencia(), e.getHashSolicitud());
    }
}
