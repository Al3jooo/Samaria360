package co.edu.unimagdalena.samaria360.chatbot.domain.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Datos de entrada inválidos: 400 {@code DATOS_INVALIDOS} con un código por campo (nombres JSON, nunca textos). */
public class DatosInvalidosException extends RuntimeException {

    private final Map<String, String> campos;

    public DatosInvalidosException(Map<String, String> campos) {
        super("Datos inválidos: " + campos.keySet());
        this.campos = Collections.unmodifiableMap(new LinkedHashMap<>(campos));
    }

    public Map<String, String> getCampos() {
        return campos;
    }
}
