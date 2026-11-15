package co.edu.unimagdalena.samaria360.catalog.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Promedio visible de las calificaciones de un servicio o un atractivo (RF17), tal como llega en
 * {@code PromedioActualizado} de ms-analytics (DEC-302; contrato en {@code docs/sad/api/eventos.md}). El catálogo
 * solo lo copia para mostrarlo y ordenar por él (RF61); la regla de cálculo es de ms-analytics.
 *
 * @param promedio de 1 a 5, o nulo si {@code cantidad = 0}
 * @param version  monótona por entidad: solo se aplica la mayor
 */
public record CalificacionEntidad(TipoOferta tipo, String idEntidad, Double promedio, long cantidad, long version) {

    private static final Pattern OBJECT_ID = Pattern.compile("^[0-9a-f]{24}$");

    public CalificacionEntidad {
        Objects.requireNonNull(tipo, "tipo");
        if (idEntidad == null || !OBJECT_ID.matcher(idEntidad).matches()) {
            throw new IllegalArgumentException("idEntidad no es un ObjectId");
        }
        if (cantidad < 0 || version < 1) {
            throw new IllegalArgumentException("cantidad desde 0 y version desde 1");
        }
        if ((cantidad == 0) != (promedio == null)) {
            throw new IllegalArgumentException("El promedio es nulo si y solo si no hay calificaciones");
        }
        if (promedio != null && (promedio < 1 || promedio > 5)) {
            throw new IllegalArgumentException("El promedio va de 1 a 5");
        }
    }

    /** {@code tipoEntidad} del evento: {@code servicio} o {@code atractivo}. */
    public static CalificacionEntidad deEvento(String tipoEntidad, String idEntidad, Double promedio, long cantidad,
                                               long version) {
        TipoOferta tipo = switch (tipoEntidad == null ? "" : tipoEntidad) {
            case "servicio" -> TipoOferta.Servicio;
            case "atractivo" -> TipoOferta.Atractivo;
            default -> throw new IllegalArgumentException("tipoEntidad desconocido: " + tipoEntidad);
        };
        return new CalificacionEntidad(tipo, idEntidad, promedio, cantidad, version);
    }
}
