package co.edu.unimagdalena.samaria360.analytics.domain.model;

import co.edu.unimagdalena.samaria360.analytics.domain.exception.CamposInvalidosException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Reporte de información falsa o desactualizada (RF57; DEC-301): un motivo de la lista predefinida y una descripción
 * opcional de hasta 500 caracteres. El Administrador lo ve en el panel de moderación y solo lo marca {@code Revisado}.
 */
public record Reporte(String id, TipoEntidad tipoEntidad, String idEntidad, UUID turistaId, Motivo motivo,
                      String descripcion, Estado estado, Instant fecha, UUID revisadoPor, Instant fechaRevision) {

    /** Lista predefinida (respuesta 5 del equipo). */
    public enum Motivo {
        INFORMACION_FALSA,
        INFORMACION_DESACTUALIZADA,
        PRECIO_INCORRECTO,
        UBICACION_INCORRECTA,
        FOTOS_NO_CORRESPONDEN,
        OTRO
    }

    public enum Estado { Pendiente, Revisado }

    public static final int DESCRIPCION_MAXIMA = 500;

    public Reporte {
        Objects.requireNonNull(tipoEntidad, "tipoEntidad");
        Objects.requireNonNull(idEntidad, "idEntidad");
        Objects.requireNonNull(turistaId, "turistaId");
        Objects.requireNonNull(motivo, "motivo");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(fecha, "fecha");
        descripcion = descripcion == null ? "" : descripcion;
    }

    public static Reporte nuevo(String tipo, String idEntidad, UUID turista, String motivo, String descripcion,
                                Instant ahora) {
        Map<String, String> campos = new LinkedHashMap<>();
        TipoEntidad tipoEntidad = null;
        try {
            tipoEntidad = TipoEntidad.desdeTexto(tipo);
        } catch (IllegalArgumentException e) {
            campos.put("tipoEntidad", "NO_PERMITIDO");
        }
        if (!Calificacion.esObjectId(idEntidad)) {
            campos.put("idEntidad", "FORMATO");
        }
        Motivo elegido = null;
        if (motivo == null || motivo.isBlank()) {
            campos.put("motivo", "REQUERIDO");
        } else {
            try {
                elegido = Motivo.valueOf(motivo.trim());
            } catch (IllegalArgumentException e) {
                campos.put("motivo", "NO_PERMITIDO");
            }
        }
        String limpia = descripcion == null ? "" : descripcion.strip();
        if (limpia.codePointCount(0, limpia.length()) > DESCRIPCION_MAXIMA) {
            campos.put("descripcion", "LONGITUD_MAXIMA");
        }
        if (!campos.isEmpty()) {
            throw new CamposInvalidosException(campos);
        }
        return new Reporte(null, tipoEntidad, idEntidad, turista, elegido, limpia, Estado.Pendiente, ahora, null, null);
    }

    public Reporte conId(String nuevoId) {
        return new Reporte(nuevoId, tipoEntidad, idEntidad, turistaId, motivo, descripcion, estado, fecha, revisadoPor,
                fechaRevision);
    }
}
