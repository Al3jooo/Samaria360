package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.web.error;

import java.util.Map;

/** Parámetro de la API con un formato inválido: 400 {@code DATOS_INVALIDOS} con un código por campo. */
public class DatosInvalidosException extends RuntimeException {

    private final Map<String, String> campos;

    public DatosInvalidosException(Map<String, String> campos) {
        super("Datos inválidos: " + campos.keySet());
        this.campos = Map.copyOf(campos);
    }

    public Map<String, String> getCampos() {
        return campos;
    }
}
