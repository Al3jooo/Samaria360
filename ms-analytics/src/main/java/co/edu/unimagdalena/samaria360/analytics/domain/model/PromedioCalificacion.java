package co.edu.unimagdalena.samaria360.analytics.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Promedio de las calificaciones **visibles** de una entidad (decisión 1 del equipo): de 1 a 5 con dos decimales, o
 * nulo sin calificaciones. Es la carga de {@code PromedioActualizado} ({@code eventos.md}).
 */
public record PromedioCalificacion(TipoEntidad tipoEntidad, String idEntidad, BigDecimal promedio, long cantidad) {

    public PromedioCalificacion {
        Objects.requireNonNull(tipoEntidad, "tipoEntidad");
        Objects.requireNonNull(idEntidad, "idEntidad");
        if (cantidad < 0) {
            throw new IllegalArgumentException("cantidad negativa");
        }
        if ((cantidad == 0) != (promedio == null)) {
            throw new IllegalArgumentException("El promedio es nulo si y solo si no hay calificaciones");
        }
    }

    /** Con la suma de estrellas y la cantidad de calificaciones visibles. */
    public static PromedioCalificacion de(TipoEntidad tipo, String idEntidad, long suma, long cantidad) {
        BigDecimal promedio = cantidad == 0 ? null
                : BigDecimal.valueOf(suma).divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);
        return new PromedioCalificacion(tipo, idEntidad, promedio, cantidad);
    }
}
