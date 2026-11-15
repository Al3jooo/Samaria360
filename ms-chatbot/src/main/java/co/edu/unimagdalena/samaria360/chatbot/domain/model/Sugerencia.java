package co.edu.unimagdalena.samaria360.chatbot.domain.model;

import java.util.Objects;

/**
 * Una recomendación del asistente con su justificación (RF36): el criterio principal (preferencias declaradas,
 * popularidad o disponibilidad) y un texto breve en el idioma de la interfaz (RF59). El criterio lo calcula el dominio
 * con datos, no el modelo de lenguaje (decisión 5 del equipo).
 */
public record Sugerencia(ItemCatalogo item, Criterio criterio, String justificacion) {

    public enum Criterio {
        PREFERENCIAS,
        POPULARIDAD,
        DISPONIBILIDAD
    }

    public Sugerencia {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(criterio, "criterio");
        Objects.requireNonNull(justificacion, "justificacion");
    }
}
