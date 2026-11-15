package co.edu.unimagdalena.samaria360.analytics.domain.model;

import java.util.Locale;

/** Lo que se califica o se reporta (RF17, RF57): un servicio o un atractivo del catálogo, como en {@code eventos.md}. */
public enum TipoEntidad {
    servicio,
    atractivo;

    /** Acepta {@code servicio}, {@code Servicio}, {@code SERVICIO}…; otro valor, {@link IllegalArgumentException}. */
    public static TipoEntidad desdeTexto(String texto) {
        if (texto == null) {
            throw new IllegalArgumentException("tipo nulo");
        }
        return valueOf(texto.trim().toLowerCase(Locale.ROOT));
    }
}
