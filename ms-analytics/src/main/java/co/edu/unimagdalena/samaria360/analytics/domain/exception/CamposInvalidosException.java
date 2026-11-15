package co.edu.unimagdalena.samaria360.analytics.domain.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/** Datos de una calificación, una moderación o un reporte que no cumplen sus reglas: un código por campo. */
public class CamposInvalidosException extends RuntimeException {

    private final Map<String, String> campos;

    public CamposInvalidosException(Map<String, String> campos) {
        super("Datos inválidos: " + campos.keySet());
        this.campos = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(campos));
    }

    public Map<String, String> getCampos() {
        return campos;
    }
}
