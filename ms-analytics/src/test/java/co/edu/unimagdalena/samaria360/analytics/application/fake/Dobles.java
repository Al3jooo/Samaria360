package co.edu.unimagdalena.samaria360.analytics.application.fake;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.FiltroAuditoria;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.PaginaAuditoria;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.AuditoriaRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.CorreoPendienteRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.CuentaProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.EventosProcesadosPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.NotificacionRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.ReservaProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.domain.model.CorreoPendiente;
import co.edu.unimagdalena.samaria360.analytics.domain.model.CuentaProyectada;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Notificacion;
import co.edu.unimagdalena.samaria360.analytics.domain.model.RegistroAuditoria;
import co.edu.unimagdalena.samaria360.analytics.domain.model.ReservaProyectada;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/** Dobles en memoria de los puertos de salida de ms-analytics; las reglas de MongoDB se prueban contra MongoDB. */
public final class Dobles {

    private Dobles() {
    }

    public static final class Procesados implements EventosProcesadosPort {
        public final Set<UUID> ids = new HashSet<>();

        @Override
        public boolean yaProcesado(UUID idEvento) {
            return ids.contains(idEvento);
        }

        @Override
        public void registrar(UUID idEvento, String tipo, Instant procesadoEn) {
            ids.add(idEvento);
        }
    }

    public static final class Cuentas implements CuentaProyeccionPort {
        public final Map<UUID, CuentaProyectada> cuentas = new HashMap<>();

        @Override
        public void actualizarSiMayor(CuentaProyectada cuenta) {
            CuentaProyectada actual = cuentas.get(cuenta.idUsuario());
            if (actual == null || actual.version() < cuenta.version()) {
                cuentas.put(cuenta.idUsuario(), cuenta);
            }
        }

        @Override
        public OptionalLong versionDe(UUID idUsuario) {
            CuentaProyectada cuenta = cuentas.get(idUsuario);
            return cuenta == null ? OptionalLong.empty() : OptionalLong.of(cuenta.version());
        }

        @Override
        public java.util.Optional<String> estadoDe(UUID idUsuario) {
            return java.util.Optional.ofNullable(cuentas.get(idUsuario)).map(CuentaProyectada::estadoCuenta);
        }
    }

    public static final class Auditoria implements AuditoriaRepositoryPort {
        public final List<RegistroAuditoria> registros = new ArrayList<>();

        @Override
        public void registrar(RegistroAuditoria registro) {
            registros.add(registro);
        }

        @Override
        public PaginaAuditoria listar(FiltroAuditoria filtro, int pagina, int tamano) {
            return new PaginaAuditoria(List.copyOf(registros), pagina, tamano, registros.size());
        }
    }

    public static final class Notificaciones implements NotificacionRepositoryPort {
        public final List<Notificacion> guardadas = new ArrayList<>();

        @Override
        public void guardar(Notificacion notificacion) {
            guardadas.add(notificacion);
        }

        @Override
        public List<Notificacion> listar(UUID usuario, int pagina, int tamano) {
            return guardadas.stream().filter(n -> n.usuarioId().equals(usuario)).toList();
        }

        @Override
        public long contar(UUID usuario) {
            return listar(usuario, 0, 0).size();
        }

        @Override
        public long contarNoLeidas(UUID usuario) {
            return guardadas.stream().filter(n -> n.usuarioId().equals(usuario) && !n.leida()).count();
        }

        @Override
        public boolean marcarLeida(UUID usuario, String idNotificacion) {
            return "propia".equals(idNotificacion);
        }

        @Override
        public long marcarTodas(UUID usuario) {
            return contarNoLeidas(usuario);
        }
    }

    public static final class Reservas implements ReservaProyeccionPort {
        public final List<ReservaProyectada> guardadas = new ArrayList<>();

        @Override
        public void guardar(ReservaProyectada reserva) {
            guardadas.add(reserva);
        }

        @Override
        public java.util.Optional<ReservaProyectada> completadaDe(UUID turista, String idServicio) {
            return guardadas.stream().filter(r -> r.idTurista().equals(turista) && r.idServicio().equals(idServicio)
                    && r.estado() == ReservaProyectada.EstadoReserva.Completada).reduce((a, b) -> b);
        }
    }

    public static final class Correos implements CorreoPendienteRepositoryPort {
        public final List<CorreoPendiente> encolados = new ArrayList<>();
        public final List<String> enviados = new ArrayList<>();
        public final Map<String, Instant> fallos = new HashMap<>();

        @Override
        public void encolar(CorreoPendiente correo) {
            encolados.add(new CorreoPendiente("c" + encolados.size(), correo.idEvento(), correo.idPrestador(),
                    correo.datos(), correo.intentos(), correo.proximoIntento()));
        }

        @Override
        public List<CorreoPendiente> reclamar(Instant ahora, Duration arriendo, int limite) {
            return encolados.stream().filter(c -> !enviados.contains(c.id()) && !fallos.containsKey(c.id())).toList();
        }

        @Override
        public void marcarEnviado(CorreoPendiente correo, Instant ahora) {
            enviados.add(correo.id());
        }

        @Override
        public void registrarFallo(CorreoPendiente correo, Instant proximoIntento, String error) {
            fallos.put(correo.id(), proximoIntento);
        }
    }

    public static final class Ocupaciones implements
            co.edu.unimagdalena.samaria360.analytics.application.port.out.OcupacionProyeccionPort {
        public final List<co.edu.unimagdalena.samaria360.analytics.domain.model.OcupacionRegistrada> guardadas =
                new ArrayList<>();

        @Override
        public void guardar(co.edu.unimagdalena.samaria360.analytics.domain.model.OcupacionRegistrada ocupacion) {
            guardadas.add(ocupacion);
        }
    }

    public static final class Consultas implements
            co.edu.unimagdalena.samaria360.analytics.application.port.out.ConsultasProyeccionPort {
        public final List<co.edu.unimagdalena.samaria360.analytics.domain.model.ConsultasFicha> sumadas =
                new ArrayList<>();

        @Override
        public void sumar(co.edu.unimagdalena.samaria360.analytics.domain.model.ConsultasFicha consultas) {
            sumadas.add(consultas);
        }

        @Override
        public java.util.Optional<String> ultimoNombre(
                co.edu.unimagdalena.samaria360.analytics.domain.model.TipoEntidad tipo, String idEntidad) {
            return sumadas.stream().filter(c -> c.tipoEntidad() == tipo && c.idEntidad().equals(idEntidad)
                    && c.nombre() != null).reduce((a, b) -> b)
                    .map(co.edu.unimagdalena.samaria360.analytics.domain.model.ConsultasFicha::nombre);
        }
    }
}
