package co.edu.unimagdalena.samaria360.analytics.domain.model;

import co.edu.unimagdalena.samaria360.analytics.domain.exception.EventoInvalidoException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada de {@code logs_auditoria} (RF13): el usuario que hizo la acción, la acción, la fecha y un detalle con los
 * identificadores afectados (sin datos personales ni texto libre, ADR-011). {@code idEvento} es el del evento que la
 * originó, o el de la acción local (RF29): con él la entrada es idempotente.
 */
public record RegistroAuditoria(UUID idEvento, UUID usuarioId, AccionAuditada accion, String detalle, Instant fecha) {

    public RegistroAuditoria {
        Objects.requireNonNull(idEvento, "idEvento");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(fecha, "fecha");
        detalle = detalle == null ? "" : detalle;
    }

    /** {@code PrestadorValidado}: lo hizo el Administrador. */
    public static RegistroAuditoria deValidacion(EventoEntrante evento) {
        String decision = evento.texto("decision");
        AccionAuditada accion = switch (decision) {
            case "Aprobado" -> AccionAuditada.PRESTADOR_APROBADO;
            case "Rechazado" -> AccionAuditada.PRESTADOR_RECHAZADO;
            default -> throw new EventoInvalidoException("PrestadorValidado: decisión desconocida " + decision);
        };
        return new RegistroAuditoria(evento.idEvento(), evento.uuid("idAdministrador"), accion,
                "Prestador " + evento.uuid("idPrestador"), evento.ocurridoEn());
    }

    /** {@code ServicioDecidido}: lo hizo el Administrador (RF21). */
    public static RegistroAuditoria deServicio(EventoEntrante evento) {
        String decision = evento.texto("decision");
        AccionAuditada accion = switch (decision) {
            case "Publicado" -> AccionAuditada.SERVICIO_APROBADO;
            case "Rechazado" -> AccionAuditada.SERVICIO_RECHAZADO;
            default -> throw new EventoInvalidoException("ServicioDecidido: decisión desconocida " + decision);
        };
        return new RegistroAuditoria(evento.idEvento(), evento.uuid("idAdministrador"), accion,
                "Servicio " + evento.texto("idServicio") + " del prestador " + evento.uuid("idPrestador"),
                evento.ocurridoEn());
    }

    /** {@code RolCambiado} (RF45): lo hizo el Administrador. */
    public static RegistroAuditoria deCambioRol(EventoEntrante evento) {
        return new RegistroAuditoria(evento.idEvento(), evento.uuid("idAdministrador"), AccionAuditada.ROL_CAMBIADO,
                "Cuenta " + evento.uuid("idUsuario") + ": " + evento.texto("rolAnterior") + " → "
                        + evento.texto("rolNuevo"), evento.ocurridoEn());
    }

    /** {@code ReservaCancelada} (RF18): la hizo {@code canceladaPor}. */
    public static RegistroAuditoria deCancelacion(EventoEntrante evento) {
        return new RegistroAuditoria(evento.idEvento(), evento.uuid("canceladaPor"), AccionAuditada.RESERVA_CANCELADA,
                "Reserva " + evento.uuid("idReserva") + " de «" + evento.texto("nombreServicio") + "»",
                evento.ocurridoEn());
    }
}
