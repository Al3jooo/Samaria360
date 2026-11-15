package co.edu.unimagdalena.samaria360.chatbot.domain.model;

import co.edu.unimagdalena.samaria360.chatbot.domain.exception.DatosInvalidosException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Entrada de {@code base_conocimiento_turistico} (Entregable 2, §2.4; RF05): el contenido que el asistente puede usar,
 * con su categoría y la fecha de la última actualización. El Administrador la crea, la edita o la elimina: el asistente
 * no responde con nada que no esté aquí o en el catálogo (respuestas controladas, RF05). Límites de DEC-297: contenido
 * de 1 a 2000 caracteres y categoría de 1 a 50, sin los espacios de los extremos.
 */
public record EntradaConocimiento(String id, String contenido, String categoria, Instant fechaActualizacion) {

    public static final int CONTENIDO_MAXIMO = 2000;
    public static final int CATEGORIA_MAXIMA = 50;

    public EntradaConocimiento {
        Objects.requireNonNull(contenido, "contenido");
        Objects.requireNonNull(categoria, "categoria");
        Objects.requireNonNull(fechaActualizacion, "fechaActualizacion");
    }

    /** Valida y recorta; {@code id} nulo para una entrada nueva. */
    public static EntradaConocimiento validar(String id, String contenido, String categoria, Instant ahora) {
        Map<String, String> campos = new LinkedHashMap<>();
        String texto = acotar(contenido, CONTENIDO_MAXIMO, "contenido", campos);
        String grupo = acotar(categoria, CATEGORIA_MAXIMA, "categoria", campos);
        if (!campos.isEmpty()) {
            throw new DatosInvalidosException(campos);
        }
        return new EntradaConocimiento(id, texto, grupo, ahora);
    }

    private static String acotar(String valor, int maximo, String campo, Map<String, String> campos) {
        String limpio = valor == null ? "" : valor.strip();
        if (limpio.isEmpty()) {
            campos.put(campo, "REQUERIDO");
        } else if (limpio.codePointCount(0, limpio.length()) > maximo) {
            campos.put(campo, "LONGITUD_MAXIMA");
        }
        return limpio;
    }
}
