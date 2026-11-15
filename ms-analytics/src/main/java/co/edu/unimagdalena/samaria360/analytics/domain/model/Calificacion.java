package co.edu.unimagdalena.samaria360.analytics.domain.model;

import co.edu.unimagdalena.samaria360.analytics.domain.exception.CamposInvalidosException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Calificación de un servicio o un atractivo (RF17; decisión 1 del equipo, DEC-300): de 1 a 5 estrellas y un comentario
 * de 0 a 500 caracteres; una por turista y entidad, sin edición. {@code estado}: {@code Visible} u {@code Oculta}
 * (RF29, reversible); eliminar es definitivo. {@code idPrestador}, solo en los servicios (sale de la reserva
 * Completada); {@code nombreEntidad}, de la reserva en un servicio y de la ficha consultada en un atractivo (DEC-323). {@code id} es nulo hasta que se guarda.
 */
public record Calificacion(String id, TipoEntidad tipoEntidad, String idEntidad, UUID turistaId, UUID idPrestador,
                           String nombreEntidad, int estrellas, String comentario, Estado estado,
                           String motivoModeracion, Instant fecha, Instant fechaModeracion) {

    public enum Estado { Visible, Oculta }

    public static final int COMENTARIO_MAXIMO = 500;
    public static final int MOTIVO_MAXIMO = 500;
    static final Pattern OBJECT_ID = Pattern.compile("^[0-9a-f]{24}$");

    public Calificacion {
        Objects.requireNonNull(tipoEntidad, "tipoEntidad");
        Objects.requireNonNull(idEntidad, "idEntidad");
        Objects.requireNonNull(turistaId, "turistaId");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(fecha, "fecha");
        comentario = comentario == null ? "" : comentario;
    }

    /** Una calificación nueva, visible, con las reglas de RF17. */
    public static Calificacion nueva(TipoEntidad tipo, String idEntidad, UUID turista, UUID idPrestador,
                                     String nombreEntidad, Integer estrellas, String comentario, Instant ahora) {
        Map<String, String> campos = new LinkedHashMap<>();
        if (tipo == null) {
            campos.put("tipoEntidad", "NO_PERMITIDO");
        }
        if (!esObjectId(idEntidad)) {
            campos.put("idEntidad", "FORMATO");
        }
        if (estrellas == null) {
            campos.put("estrellas", "REQUERIDO");
        } else if (estrellas < 1 || estrellas > 5) {
            campos.put("estrellas", "VALOR_FUERA_DE_RANGO");
        }
        String limpio = comentario == null ? "" : comentario.strip();
        if (limpio.codePointCount(0, limpio.length()) > COMENTARIO_MAXIMO) {
            campos.put("comentario", "LONGITUD_MAXIMA");
        }
        if (!campos.isEmpty()) {
            throw new CamposInvalidosException(campos);
        }
        return new Calificacion(null, tipo, idEntidad, turista, idPrestador, nombreEntidad, estrellas, limpio,
                Estado.Visible, null, ahora, null);
    }

    /** RF29: el motivo de la moderación, de 1 a 500 caracteres, recortado. */
    public static String motivoValido(String motivo) {
        String limpio = motivo == null ? "" : motivo.strip();
        if (limpio.isEmpty()) {
            throw new CamposInvalidosException(Map.of("motivo", "REQUERIDO"));
        }
        if (limpio.codePointCount(0, limpio.length()) > MOTIVO_MAXIMO) {
            throw new CamposInvalidosException(Map.of("motivo", "LONGITUD_MAXIMA"));
        }
        return limpio;
    }

    public static boolean esObjectId(String texto) {
        return texto != null && OBJECT_ID.matcher(texto).matches();
    }

    public Calificacion conId(String nuevoId) {
        return new Calificacion(nuevoId, tipoEntidad, idEntidad, turistaId, idPrestador, nombreEntidad, estrellas,
                comentario, estado, motivoModeracion, fecha, fechaModeracion);
    }
}
