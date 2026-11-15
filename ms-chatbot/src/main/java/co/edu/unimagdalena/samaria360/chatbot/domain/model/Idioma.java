package co.edu.unimagdalena.samaria360.chatbot.domain.model;

import java.util.Locale;

/** Idioma de la interfaz (RF03): el asistente responde en el mismo (RF59). Español si no se reconoce. */
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
