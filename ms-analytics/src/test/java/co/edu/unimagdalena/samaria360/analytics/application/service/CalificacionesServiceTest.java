package co.edu.unimagdalena.samaria360.analytics.application.service;

import co.edu.unimagdalena.samaria360.analytics.application.fake.Dobles;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.CalificarUseCase.ComandoCalificar;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.FiltroCalificacionesPrestador;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.Pagina;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ReportarUseCase.ComandoReportar;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ResumenServicio;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.CalificacionRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.PublicacionPendiente;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.ReporteRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.domain.exception.ReglaNegocioException;
import co.edu.unimagdalena.samaria360.analytics.domain.model.AccionAuditada;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Calificacion;
import co.edu.unimagdalena.samaria360.analytics.domain.model.CuentaProyectada;
import co.edu.unimagdalena.samaria360.analytics.domain.model.EventoAnalitica;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Reporte;
import co.edu.unimagdalena.samaria360.analytics.domain.model.ReservaProyectada;
import co.edu.unimagdalena.samaria360.analytics.domain.model.TipoEntidad;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF17, RF29 y RF57 con dobles de los puertos (decisión 1 del equipo, DEC-300, DEC-301). */
class CalificacionesServiceTest {

    private static final Instant AHORA = Instant.parse("2030-03-11T15:00:00Z");
    private static final String SERVICIO = "6a0000000000000000000001";
    private static final String ATRACTIVO = "6a0000000000000000000009";
    private static final UUID TURISTA = UUID.randomUUID();
    private static final UUID PRESTADOR = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    /** Calificaciones en memoria, con la regla del índice único. */
    static final class Calificaciones implements CalificacionRepositoryPort {
        final Map<String, Calificacion> datos = new HashMap<>();

        @Override
        public boolean existe(UUID turista, TipoEntidad tipo, String idEntidad) {
            return deTurista(turista, tipo, idEntidad).isPresent();
        }

        @Override
        public Optional<Calificacion> deTurista(UUID turista, TipoEntidad tipo, String idEntidad) {
            return datos.values().stream().filter(c -> c.turistaId().equals(turista) && c.tipoEntidad() == tipo
                    && c.idEntidad().equals(idEntidad)).findFirst();
        }

        @Override
        public Calificacion insertar(Calificacion c) {
            String id = String.format("%024x", datos.size() + 1);
            datos.put(id, c.conId(id));
            return c.conId(id);
        }

        @Override
        public Optional<Calificacion> buscar(String id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public boolean cambiarEstado(String id, Calificacion.Estado esperado, Calificacion.Estado nuevo, String motivo,
                                     Instant fecha) {
            Calificacion c = datos.get(id);
            if (c == null || c.estado() != esperado) {
                return false;
            }
            datos.put(id, new Calificacion(id, c.tipoEntidad(), c.idEntidad(), c.turistaId(), c.idPrestador(),
                    c.nombreEntidad(), c.estrellas(), c.comentario(), nuevo, motivo, c.fecha(), fecha));
            return true;
        }

        @Override
        public boolean eliminar(String id) {
            return datos.remove(id) != null;
        }

        private List<Calificacion> visiblesDe(TipoEntidad tipo, String id) {
            return datos.values().stream().filter(c -> c.tipoEntidad() == tipo && c.idEntidad().equals(id)
                    && c.estado() == Calificacion.Estado.Visible).toList();
        }

        @Override
        public long[] sumaYCantidadVisibles(TipoEntidad tipo, String idEntidad) {
            List<Calificacion> v = visiblesDe(tipo, idEntidad);
            return new long[]{v.stream().mapToLong(Calificacion::estrellas).sum(), v.size()};
        }

        @Override
        public Pagina<Calificacion> visibles(TipoEntidad tipo, String idEntidad, int pagina, int tamano) {
            List<Calificacion> v = visiblesDe(tipo, idEntidad);
            return new Pagina<>(v, pagina, tamano, v.size());
        }

        @Override
        public Pagina<Calificacion> delPrestador(FiltroCalificacionesPrestador filtro, int pagina, int tamano) {
            List<Calificacion> v = datos.values().stream().filter(c -> filtro.prestador().equals(c.idPrestador()))
                    .toList();
            return new Pagina<>(v, pagina, tamano, v.size());
        }

        @Override
        public long[] sumaYCantidadDelPrestador(FiltroCalificacionesPrestador filtro) {
            List<Calificacion> v = delPrestador(filtro, 0, 0).elementos();
            return new long[]{v.stream().mapToLong(Calificacion::estrellas).sum(), v.size()};
        }

        @Override
        public List<ResumenServicio> serviciosDelPrestador(UUID prestador) {
            return List.of();
        }

        @Override
        public Pagina<Calificacion> paraModerar(Calificacion.Estado estado, int pagina, int tamano) {
            List<Calificacion> v = datos.values().stream().filter(c -> estado == null || c.estado() == estado).toList();
            return new Pagina<>(v, pagina, tamano, v.size());
        }
    }

    static final class Outbox implements OutboxEventosPort {
        final List<EventoAnalitica> eventos = new ArrayList<>();

        @Override
        public void registrar(EventoAnalitica evento) {
            eventos.add(evento);
        }

        @Override
        public List<PublicacionPendiente> reclamar(Instant ahora, Duration arriendo, int limite) {
            return List.of();
        }

        @Override
        public void marcarPublicado(PublicacionPendiente publicacion, Instant ahora) {
        }

        @Override
        public void registrarFallo(PublicacionPendiente publicacion, Instant proximoIntento, String error) {
        }

        @Override
        public void liberar(List<PublicacionPendiente> publicaciones, Instant ahora) {
        }

        @Override
        public int purgarPublicados(Instant limite) {
            return 0;
        }
    }

    private final Calificaciones calificaciones = new Calificaciones();
    private final Map<String, Long> versiones = new HashMap<>();
    private final Dobles.Reservas reservas = new Dobles.Reservas();
    private final Dobles.Cuentas cuentas = new Dobles.Cuentas();
    private final Dobles.Auditoria auditoria = new Dobles.Auditoria();
    private final Outbox outbox = new Outbox();
    private final Dobles.Consultas fichas = new Dobles.Consultas();
    private final CalificacionesService servicio = new CalificacionesService(calificaciones,
            p -> versiones.merge(p.tipoEntidad() + ":" + p.idEntidad(), 1L, Long::sum), reservas, cuentas, auditoria,
            outbox, fichas, () -> AHORA);

    private void completada() {
        reservas.guardar(new ReservaProyectada(UUID.randomUUID(), TURISTA, PRESTADOR, SERVICIO, "Tour Tayrona",
                AHORA.minusSeconds(86_400), 2, ReservaProyectada.EstadoReserva.Completada, AHORA));
    }

    private ComandoCalificar comando(String tipo, String id, int estrellas) {
        return new ComandoCalificar(TURISTA, tipo, id, estrellas, "Muy bueno");
    }

    @Test
    void conUnaReservaCompletadaCalificaYPublicaElPromedio() {
        completada();

        Calificacion c = servicio.calificar(comando("servicio", SERVICIO, 4));

        assertThat(c.idPrestador()).isEqualTo(PRESTADOR);
        assertThat(c.nombreEntidad()).isEqualTo("Tour Tayrona");
        assertThat(outbox.eventos).singleElement().satisfies(e -> assertThat(e.datos())
                .containsEntry("promedio", 4.0).containsEntry("cantidad", 1L).containsEntry("version", 1L));
    }

    @Test
    void unServicioSinReservaCompletadaNoSeCalifica() {
        reservas.guardar(new ReservaProyectada(UUID.randomUUID(), TURISTA, PRESTADOR, SERVICIO, "Tour", AHORA, 2,
                ReservaProyectada.EstadoReserva.Confirmada, AHORA));

        assertThatThrownBy(() -> servicio.calificar(comando("servicio", SERVICIO, 4)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(ReglaNegocioException.SIN_RESERVA_COMPLETADA));
        assertThat(servicio.elegibilidad(TURISTA, "servicio", SERVICIO).motivo())
                .isEqualTo(ReglaNegocioException.SIN_RESERVA_COMPLETADA);
        assertThat(outbox.eventos).isEmpty();
    }

    @Test
    void unAtractivoLoCalificaCualquierTuristaActivoUnaSolaVez() {
        assertThat(servicio.elegibilidad(TURISTA, "atractivo", ATRACTIVO).puedeCalificar()).isTrue();
        servicio.calificar(comando("atractivo", ATRACTIVO, 5));

        assertThatThrownBy(() -> servicio.calificar(comando("atractivo", ATRACTIVO, 3)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(ReglaNegocioException.CALIFICACION_DUPLICADA));
        assertThat(servicio.elegibilidad(TURISTA, "atractivo", ATRACTIVO).propia().estrellas()).isEqualTo(5);
    }

    @Test
    void laModeracionMuestraElNombreDelAtractivoQueTrajoSuFicha() {
        Calificacion sinNombre = servicio.calificar(comando("atractivo", ATRACTIVO, 4));
        assertThat(sinNombre.nombreEntidad()).isNull();

        // DEC-323: la ficha consultada trajo el nombre (ConsultasAgregadas); la moderación lo completa al listar.
        fichas.sumar(new co.edu.unimagdalena.samaria360.analytics.domain.model.ConsultasFicha(
                co.edu.unimagdalena.samaria360.analytics.domain.model.TipoEntidad.atractivo, ATRACTIVO,
                "Playa Cristal", null, java.time.LocalDate.of(2030, 3, 1), 3));

        assertThat(servicio.listar(null, 0).elementos()).extracting(Calificacion::nombreEntidad)
                .containsExactly("Playa Cristal");
        Calificacion conNombre = servicio.calificar(new ComandoCalificar(UUID.randomUUID(), "atractivo", ATRACTIVO,
                5, ""));
        assertThat(conNombre.nombreEntidad()).isEqualTo("Playa Cristal");
    }

    @Test
    void unaCuentaDesactivadaNoCalifica() {
        cuentas.actualizarSiMayor(new CuentaProyectada(TURISTA, "Turista", "Desactivado", 3, "CO"));

        assertThatThrownBy(() -> servicio.calificar(comando("atractivo", ATRACTIVO, 5)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(ReglaNegocioException.CUENTA_INACTIVA));
    }

    /** RF29: ocultar es reversible, cuenta solo lo visible y deja el motivo en la auditoría; eliminar es definitivo. */
    @Test
    void laModeracionRecalculaElPromedioYQuedaEnLaAuditoriaConSuMotivo() {
        Calificacion c = servicio.calificar(comando("atractivo", ATRACTIVO, 1));
        servicio.calificar(new ComandoCalificar(UUID.randomUUID(), "atractivo", ATRACTIVO, 5, ""));

        servicio.ocultar(c.id(), ADMIN, "Lenguaje ofensivo");
        assertThat(outbox.eventos.get(outbox.eventos.size() - 1).datos()).containsEntry("promedio", 5.0)
                .containsEntry("cantidad", 1L).containsEntry("version", 3L);
        assertThatThrownBy(() -> servicio.ocultar(c.id(), ADMIN, "otra vez"))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.getCodigo()).isEqualTo(ReglaNegocioException.ESTADO_CAMBIADO);
                    assertThat(e.getExtra()).containsEntry("estadoActual", "Oculta");
                });

        servicio.mostrar(c.id(), ADMIN, "Revisado: no incumple");
        servicio.eliminar(c.id(), ADMIN, "Spam");

        assertThat(calificaciones.buscar(c.id())).isEmpty();
        assertThat(auditoria.registros).extracting(r -> r.accion()).containsExactly(
                AccionAuditada.CALIFICACION_OCULTADA, AccionAuditada.CALIFICACION_MOSTRADA,
                AccionAuditada.CALIFICACION_ELIMINADA);
        assertThat(auditoria.registros.get(0).detalle()).contains("Motivo: Lenguaje ofensivo");
        assertThat(auditoria.registros).allMatch(r -> r.usuarioId().equals(ADMIN));
        assertThatThrownBy(() -> servicio.eliminar(c.id(), ADMIN, "x"))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(ReglaNegocioException.CALIFICACION_NO_ENCONTRADA));
    }

    @Test
    void elPrestadorVeElPromedioDeSusServicios() {
        completada();
        servicio.calificar(comando("servicio", SERVICIO, 3));

        var resultado = servicio.delPrestador(new FiltroCalificacionesPrestador(PRESTADOR, null, null, null), 0);

        assertThat(resultado.promedio().cantidad()).isEqualTo(1);
        assertThat(resultado.pagina().elementos()).singleElement()
                .satisfies(c -> assertThat(c.nombreEntidad()).isEqualTo("Tour Tayrona"));
    }

    /** RF57: un reporte pendiente por turista y entidad; el Administrador solo lo marca revisado. */
    @Test
    void losReportesSeMarcanRevisadosUnaSolaVez() {
        List<Reporte> guardados = new ArrayList<>();
        ReporteRepositoryPort repositorio = new ReporteRepositoryPort() {
            @Override
            public boolean existePendiente(UUID turista, TipoEntidad tipo, String idEntidad) {
                return guardados.stream().anyMatch(r -> r.turistaId().equals(turista) && r.idEntidad().equals(idEntidad)
                        && r.estado() == Reporte.Estado.Pendiente);
            }

            @Override
            public Reporte insertar(Reporte r) {
                Reporte conId = r.conId(String.format("%024x", guardados.size() + 1));
                guardados.add(conId);
                return conId;
            }

            @Override
            public Optional<Reporte> buscar(String id) {
                return guardados.stream().filter(r -> r.id().equals(id)).findFirst();
            }

            @Override
            public Pagina<Reporte> listar(Reporte.Estado estado, int pagina, int tamano) {
                return new Pagina<>(guardados, pagina, tamano, guardados.size());
            }

            @Override
            public boolean marcarRevisado(String id, UUID administrador, Instant fecha) {
                Reporte r = buscar(id).orElseThrow();
                if (r.estado() != Reporte.Estado.Pendiente) {
                    return false;
                }
                guardados.set(guardados.indexOf(r), new Reporte(r.id(), r.tipoEntidad(), r.idEntidad(), r.turistaId(),
                        r.motivo(), r.descripcion(), Reporte.Estado.Revisado, r.fecha(), administrador, fecha));
                return true;
            }
        };
        ReportesService reportes = new ReportesService(repositorio, cuentas, () -> AHORA);

        Reporte r = reportes.reportar(new ComandoReportar(TURISTA, "servicio", SERVICIO, "INFORMACION_FALSA", "No existe"));
        assertThatThrownBy(() -> reportes.reportar(new ComandoReportar(TURISTA, "servicio", SERVICIO, "OTRO", null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(ReglaNegocioException.REPORTE_DUPLICADO));

        assertThat(reportes.marcarRevisado(r.id(), ADMIN).revisadoPor()).isEqualTo(ADMIN);
        assertThatThrownBy(() -> reportes.marcarRevisado(r.id(), ADMIN))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(ReglaNegocioException.ESTADO_CAMBIADO));
        assertThat(reportes.reportar(new ComandoReportar(TURISTA, "servicio", SERVICIO, "OTRO", null)).estado())
                .isEqualTo(Reporte.Estado.Pendiente);
    }
}
