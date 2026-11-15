package co.edu.unimagdalena.samaria360.analytics.application.service;

import co.edu.unimagdalena.samaria360.analytics.application.port.out.ConsultasProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.domain.model.ConsultasFicha;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.OcupacionProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.domain.model.OcupacionRegistrada;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ProcesarEventoUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.AuditoriaRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.CorreoPendienteRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.CuentaProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.EventosProcesadosPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.NotificacionRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.ReservaProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.domain.model.CorreoPendiente;
import co.edu.unimagdalena.samaria360.analytics.domain.model.CuentaProyectada;
import co.edu.unimagdalena.samaria360.analytics.domain.model.EventoEntrante;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Notificacion;
import co.edu.unimagdalena.samaria360.analytics.domain.model.RegistroAuditoria;
import co.edu.unimagdalena.samaria360.analytics.domain.model.ReservaProyectada;
import co.edu.unimagdalena.samaria360.analytics.domain.model.ReservaProyectada.EstadoReserva;
import co.edu.unimagdalena.samaria360.analytics.domain.model.TipoNotificacion;

import java.util.Objects;

/**
 * Aplica cada evento de RabbitMQ (ADR-011; contrato en {@code eventos.md}), una sola vez por {@code idEvento}: el
 * decorador de infraestructura lo envuelve en una transacción de MongoDB con la marca de deduplicación (ADR-011,
 * punto 5). Un tipo desconocido se ignora: lo puede publicar una versión más nueva de otro servicio (punto 8).
 * <ul>
 *   <li>{@code CuentaActualizada}: proyección de cuentas (validador de {@code ver}, DEC-110; país, RF14).</li>
 *   <li>{@code PrestadorValidado}, {@code RolCambiado}, {@code ServicioDecidido}: auditoría (RF13).</li>
 *   <li>{@code ReservaConfirmada}: notificación al Turista y al Prestador (RF63) y correo al Prestador (RF30).</li>
 *   <li>{@code ReservaCancelada}: auditoría (RF13) y notificación al Turista (RF63).</li>
 *   <li>{@code ReservaCompletada}: notificación al Turista (RF63); habilita la calificación (RF17).</li>
 * </ul>
 * Todas las de reservas actualizan además la proyección de reservas (tableros, RF14 y RF31).
 */
public class ProcesarEventoService implements ProcesarEventoUseCase {

    private final EventosProcesadosPort procesados;
    private final CuentaProyeccionPort cuentas;
    private final AuditoriaRepositoryPort auditoria;
    private final NotificacionRepositoryPort notificaciones;
    private final ReservaProyeccionPort reservas;
    private final CorreoPendienteRepositoryPort correos;
    private final OcupacionProyeccionPort ocupaciones;
    private final ConsultasProyeccionPort consultas;
    private final RelojPort reloj;

    public ProcesarEventoService(EventosProcesadosPort procesados, CuentaProyeccionPort cuentas,
                                 AuditoriaRepositoryPort auditoria, NotificacionRepositoryPort notificaciones,
                                 ReservaProyeccionPort reservas, CorreoPendienteRepositoryPort correos,
                                 OcupacionProyeccionPort ocupaciones, ConsultasProyeccionPort consultas,
                                 RelojPort reloj) {
        this.procesados = Objects.requireNonNull(procesados);
        this.cuentas = Objects.requireNonNull(cuentas);
        this.auditoria = Objects.requireNonNull(auditoria);
        this.notificaciones = Objects.requireNonNull(notificaciones);
        this.reservas = Objects.requireNonNull(reservas);
        this.correos = Objects.requireNonNull(correos);
        this.ocupaciones = Objects.requireNonNull(ocupaciones);
        this.consultas = Objects.requireNonNull(consultas);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public boolean procesar(EventoEntrante evento) {
        if (procesados.yaProcesado(evento.idEvento())) {
            return false;
        }
        switch (evento.tipo()) {
            case "CuentaActualizada" -> cuentas.actualizarSiMayor(CuentaProyectada.de(evento));
            case "PrestadorValidado" -> auditoria.registrar(RegistroAuditoria.deValidacion(evento));
            case "RolCambiado" -> auditoria.registrar(RegistroAuditoria.deCambioRol(evento));
            case "ServicioDecidido" -> auditoria.registrar(RegistroAuditoria.deServicio(evento));
            case "ReservaConfirmada" -> confirmada(evento);
            case "ReservaCancelada" -> cancelada(evento);
            case "ReservaCompletada" -> completada(evento);
            // DEC-304: el historial de afluencia que usa RF64 (iteración K).
            case "OcupacionRegistrada" -> ocupaciones.guardar(OcupacionRegistrada.de(evento));
            // DEC-308: las consultas a las fichas por día (RF14, top 3; RF31, visualizaciones del Prestador).
            case "ConsultasAgregadas" -> ConsultasFicha.de(evento).forEach(consultas::sumar);
            default -> {
                // Tipo nuevo de otro servicio: se marca procesado y se ignora (ADR-011, punto 8).
            }
        }
        procesados.registrar(evento.idEvento(), evento.tipo(), reloj.ahora());
        return true;
    }

    private void confirmada(EventoEntrante evento) {
        reservas.guardar(ReservaProyectada.de(evento, EstadoReserva.Confirmada, evento.instante("fechaInicio"),
                (int) evento.entero("personas")));
        notificaciones.guardar(Notificacion.alTurista(TipoNotificacion.RESERVA_CONFIRMADA, evento,
                evento.instante("fechaInicio")));
        Notificacion alPrestador = Notificacion.alPrestador(evento);
        notificaciones.guardar(alPrestador);
        correos.encolar(CorreoPendiente.de(alPrestador, reloj.ahora()));
    }

    private void cancelada(EventoEntrante evento) {
        reservas.guardar(ReservaProyectada.de(evento, EstadoReserva.Cancelada, evento.instante("fechaInicio"),
                (int) evento.entero("personas")));
        auditoria.registrar(RegistroAuditoria.deCancelacion(evento));
        notificaciones.guardar(Notificacion.alTurista(TipoNotificacion.RESERVA_CANCELADA, evento,
                evento.instante("fechaInicio")));
    }

    private void completada(EventoEntrante evento) {
        reservas.guardar(ReservaProyectada.de(evento, EstadoReserva.Completada, null, 0));
        notificaciones.guardar(Notificacion.alTurista(TipoNotificacion.RESERVA_COMPLETADA, evento,
                evento.instante("fechaFin")));
    }
}
