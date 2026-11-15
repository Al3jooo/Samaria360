package co.edu.unimagdalena.samaria360.analytics.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Notificación interna (RF63; colección {@code notificaciones} del Entregable 2, §2.4): destinatario, tipo, mensaje en
 * español (respaldo), indicador de lectura y fecha. {@code datos} (ampliación, DEC-292) lleva los valores con los que la
 * interfaz arma el texto en el idioma activo (RF03): el servicio, la fecha de la actividad y las personas. Sin datos
 * personales de terceros: el prestador no ve el nombre del turista. {@code id} es nulo hasta guardarla.
 */
public record Notificacion(String id, UUID idEvento, UUID usuarioId, TipoNotificacion tipo, String mensaje,
                           Map<String, String> datos, boolean leida, Instant fecha) {

    /** Fechas de las actividades en la hora de Santa Marta, como en ms-booking. */
    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    public Notificacion {
        Objects.requireNonNull(idEvento, "idEvento");
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(mensaje, "mensaje");
        Objects.requireNonNull(fecha, "fecha");
        datos = datos == null ? Map.of() : Map.copyOf(datos);
    }

    /** Al Turista, por un cambio de estado de su reserva (RF63). */
    public static Notificacion alTurista(TipoNotificacion tipo, EventoEntrante evento, Instant fechaActividad) {
        String servicio = evento.texto("nombreServicio");
        String dia = LocalDate.ofInstant(fechaActividad, ZONA).toString();
        String mensaje = switch (tipo) {
            case RESERVA_CONFIRMADA -> "Tu reserva de «" + servicio + "» para el " + dia + " quedó confirmada.";
            case RESERVA_CANCELADA -> "Tu reserva de «" + servicio + "» para el " + dia + " quedó cancelada.";
            case RESERVA_COMPLETADA -> "Tu reserva de «" + servicio + "» quedó completada. Ya puedes calificar el servicio.";
            case NUEVA_RESERVA -> throw new IllegalArgumentException("NUEVA_RESERVA es para el Prestador");
        };
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("idReserva", evento.uuid("idReserva").toString());
        datos.put("idServicio", evento.texto("idServicio"));
        datos.put("nombreServicio", servicio);
        datos.put("fecha", dia);
        return new Notificacion(null, evento.idEvento(), evento.uuid("idTurista"), tipo, mensaje, datos, false,
                evento.ocurridoEn());
    }

    /** Al Prestador, por una reserva nueva (RF30 y RF63): la actividad, la fecha y la cantidad de personas. */
    public static Notificacion alPrestador(EventoEntrante evento) {
        String servicio = evento.texto("nombreServicio");
        String dia = LocalDate.ofInstant(evento.instante("fechaInicio"), ZONA).toString();
        long personas = evento.entero("personas");
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("idReserva", evento.uuid("idReserva").toString());
        datos.put("idServicio", evento.texto("idServicio"));
        datos.put("nombreServicio", servicio);
        datos.put("fecha", dia);
        datos.put("personas", Long.toString(personas));
        String mensaje = "Nueva reserva de «" + servicio + "» para el " + dia + ": " + personas
                + (personas == 1 ? " persona." : " personas.");
        return new Notificacion(null, evento.idEvento(), evento.uuid("idPrestador"), TipoNotificacion.NUEVA_RESERVA,
                mensaje, datos, false, evento.ocurridoEn());
    }
}
