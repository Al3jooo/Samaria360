package co.edu.unimagdalena.samaria360.analytics.domain.model;

import co.edu.unimagdalena.samaria360.analytics.domain.exception.EventoInvalidoException;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada de {@code logs_auditoria} (RF13): el usuario que hizo la acción, la acción, la fecha y un detalle con los
 * identificadores afectados (sin datos personales ni texto libre, ADR-011). {@code idEvento} es el del evento que la
 * originó, o el de la acción local (RF29): con él la entrada es idempotente.
 * <p>
 * {@code datos} (DEC-322): los mismos valores del detalle, por separado, para que la interfaz arme la frase en el idioma
 * activo (RF03, RNF08). {@code detalle} se conserva en español: las entradas anteriores a A005 solo lo tienen.
 */
public record RegistroAuditoria(UUID idEvento, UUID usuarioId, AccionAuditada accion, String detalle,
                                Map<String, String> datos, Instant fecha) {

    public RegistroAuditoria {
        Objects.requireNonNull(idEvento, "idEvento");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(fecha, "fecha");
        detalle = detalle == null ? "" : detalle;
        datos = datos == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(datos));
    }

    /** {@code PrestadorValidado}: lo hizo el Administrador. */
    public static RegistroAuditoria deValidacion(EventoEntrante evento) {
        String decision = evento.texto("decision");
        AccionAuditada accion = switch (decision) {
            case "Aprobado" -> AccionAuditada.PRESTADOR_APROBADO;
            case "Rechazado" -> AccionAuditada.PRESTADOR_RECHAZADO;
            default -> throw new EventoInvalidoException("PrestadorValidado: decisión desconocida " + decision);
        };
        String prestador = evento.uuid("idPrestador").toString();
        return new RegistroAuditoria(evento.idEvento(), evento.uuid("idAdministrador"), accion,
                "Prestador " + prestador, datos("idPrestador", prestador), evento.ocurridoEn());
    }

    /** {@code ServicioDecidido}: lo hizo el Administrador (RF21). */
    public static RegistroAuditoria deServicio(EventoEntrante evento) {
        String decision = evento.texto("decision");
        AccionAuditada accion = switch (decision) {
            case "Publicado" -> AccionAuditada.SERVICIO_APROBADO;
            case "Rechazado" -> AccionAuditada.SERVICIO_RECHAZADO;
            default -> throw new EventoInvalidoException("ServicioDecidido: decisión desconocida " + decision);
        };
        String servicio = evento.texto("idServicio");
        String prestador = evento.uuid("idPrestador").toString();
        Map<String, String> datos = datos("idServicio", servicio, "idPrestador", prestador);
        // DEC-323: el nombre comercial, si el evento lo trae (los anteriores no lo traen).
        evento.textoOpcional("nombreServicio").ifPresent(nombre -> datos.put("nombreServicio", nombre));
        String nombre = datos.containsKey("nombreServicio") ? " «" + datos.get("nombreServicio") + "»" : "";
        return new RegistroAuditoria(evento.idEvento(), evento.uuid("idAdministrador"), accion,
                "Servicio " + servicio + nombre + " del prestador " + prestador, datos, evento.ocurridoEn());
    }

    /** {@code RolCambiado} (RF45): lo hizo el Administrador. */
    public static RegistroAuditoria deCambioRol(EventoEntrante evento) {
        String usuario = evento.uuid("idUsuario").toString();
        String anterior = evento.texto("rolAnterior");
        String nuevo = evento.texto("rolNuevo");
        return new RegistroAuditoria(evento.idEvento(), evento.uuid("idAdministrador"), AccionAuditada.ROL_CAMBIADO,
                "Cuenta " + usuario + ": " + anterior + " → " + nuevo,
                datos("idUsuario", usuario, "rolAnterior", anterior, "rolNuevo", nuevo), evento.ocurridoEn());
    }

    /** {@code ReservaCancelada} (RF18): la hizo {@code canceladaPor}. */
    public static RegistroAuditoria deCancelacion(EventoEntrante evento) {
        String reserva = evento.uuid("idReserva").toString();
        String servicio = evento.texto("nombreServicio");
        return new RegistroAuditoria(evento.idEvento(), evento.uuid("canceladaPor"), AccionAuditada.RESERVA_CANCELADA,
                "Reserva " + reserva + " de «" + servicio + "»",
                datos("idReserva", reserva, "nombreServicio", servicio), evento.ocurridoEn());
    }

    /**
     * Moderación de una calificación (RF29): lo hizo el Administrador, con su motivo (excepción a «sin texto libre» de
     * DEC-294, registrada en DEC-300).
     */
    public static RegistroAuditoria deModeracion(UUID idEvento, UUID administrador, AccionAuditada accion,
                                                 String idCalificacion, TipoEntidad tipo, String idEntidad,
                                                 String motivo, Instant fecha) {
        return new RegistroAuditoria(idEvento, administrador, accion,
                "Calificación " + idCalificacion + " de " + tipo.name() + " " + idEntidad + ". Motivo: " + motivo,
                datos("idCalificacion", idCalificacion, "tipoEntidad", tipo.name(), "idEntidad", idEntidad,
                        "motivo", motivo), fecha);
    }

    private static Map<String, String> datos(String... claveValor) {
        Map<String, String> datos = new LinkedHashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            datos.put(claveValor[i], claveValor[i + 1]);
        }
        return datos;
    }
}
