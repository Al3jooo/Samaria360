package co.edu.unimagdalena.samaria360.auth.domain.model;

import java.util.Locale;

/** Idioma de la interfaz (RF03): el de los correos que envía ms-auth. Español si no se indica o no se reconoce. */
public enum Idioma {
    es,
    en;

    public static Idioma desdeTexto(String texto) {
        if (texto == null) {
            return es;
        }
        return texto.trim().toLowerCase(Locale.ROOT).startsWith("en") ? en : es;
    }
}
