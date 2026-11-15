package co.edu.unimagdalena.samaria360.analytics.domain.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Una regla de las calificaciones o los reportes impide la operación (DEC-300, DEC-301). {@code codigo} es el del
 * ProblemDetail; la API decide el estado HTTP. {@code extra} son propiedades adicionales (p. ej. {@code estadoActual}).
 */
public class ReglaNegocioException extends RuntimeException {

    public static final String CALIFICACION_DUPLICADA = "CALIFICACION_DUPLICADA";
    public static final String SIN_RESERVA_COMPLETADA = "SIN_RESERVA_COMPLETADA";
    public static final String CUENTA_INACTIVA = "CUENTA_INACTIVA";
    public static final String CALIFICACION_NO_ENCONTRADA = "CALIFICACION_NO_ENCONTRADA";
    public static final String REPORTE_DUPLICADO = "REPORTE_DUPLICADO";
    public static final String REPORTE_NO_ENCONTRADO = "REPORTE_NO_ENCONTRADO";
    public static final String ESTADO_CAMBIADO = "ESTADO_CAMBIADO";

    private final String codigo;
    private final Map<String, Object> extra;

    public ReglaNegocioException(String codigo) {
        this(codigo, Map.of());
    }

    public ReglaNegocioException(String codigo, Map<String, Object> extra) {
        super(codigo);
        this.codigo = codigo;
        this.extra = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(extra));
    }

    public String getCodigo() {
        return codigo;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }
}
