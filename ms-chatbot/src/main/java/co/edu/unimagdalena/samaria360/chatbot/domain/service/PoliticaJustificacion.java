package co.edu.unimagdalena.samaria360.chatbot.domain.service;

import co.edu.unimagdalena.samaria360.chatbot.domain.model.Idioma;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.ItemCatalogo;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Sugerencia;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Sugerencia.Criterio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * RF36 con datos (decisión 5 del equipo; DEC-298, decisión del equipo, no del SRS): el criterio principal de cada
 * sugerencia, en este orden de precedencia:
 * <ol>
 *   <li>{@code PREFERENCIAS}: la categoría del ítem coincide con una categoría de interés del turista (Naturaleza,
 *       Cultura o Aventura; Aventura corresponde a los tours y al alquiler de equipos);</li>
 *   <li>{@code POPULARIDAD}: su calificación promedio es de 4 o más (RF17, RF61);</li>
 *   <li>{@code DISPONIBILIDAD}: está publicado y, si es un atractivo, abierto (RF33).</li>
 * </ol>
 * Los atractivos cerrados o en mantenimiento no se sugieren. Las sugerencias van ordenadas por criterio y, dentro de
 * cada uno, por calificación.
 */
public class PoliticaJustificacion {

    static final double POPULAR = 4.0;
    static final int MAXIMO = 6;

    public List<Sugerencia> sugerir(List<ItemCatalogo> items, Set<String> intereses, Idioma idioma) {
        List<Sugerencia> sugerencias = new ArrayList<>();
        for (ItemCatalogo item : items) {
            if (!item.disponible()) {
                continue;
            }
            Criterio criterio = criterio(item, intereses);
            sugerencias.add(new Sugerencia(item, criterio, justificacion(item, criterio, idioma)));
        }
        sugerencias.sort(Comparator.comparing((Sugerencia s) -> s.criterio().ordinal())
                .thenComparing(s -> s.item().calificacion() == null ? 0.0 : -s.item().calificacion()));
        return sugerencias.size() > MAXIMO ? sugerencias.subList(0, MAXIMO) : sugerencias;
    }

    static Criterio criterio(ItemCatalogo item, Set<String> intereses) {
        if (coincideConIntereses(item, intereses)) {
            return Criterio.PREFERENCIAS;
        }
        if (item.calificacion() != null && item.calificacion() >= POPULAR) {
            return Criterio.POPULARIDAD;
        }
        return Criterio.DISPONIBILIDAD;
    }

    private static boolean coincideConIntereses(ItemCatalogo item, Set<String> intereses) {
        if (intereses == null || intereses.isEmpty() || item.categoria() == null) {
            return false;
        }
        if (intereses.contains(item.categoria())) {
            return true;
        }
        boolean aventura = "Tour".equals(item.categoria()) || "AlquilerEquipo".equals(item.categoria());
        return aventura && intereses.contains("Aventura");
    }

    static String justificacion(ItemCatalogo item, Criterio criterio, Idioma idioma) {
        boolean ingles = idioma == Idioma.en;
        return switch (criterio) {
            case PREFERENCIAS -> ingles
                    ? "Matches your declared interests (" + item.categoria() + ")."
                    : "Coincide con tus intereses declarados (" + item.categoria() + ").";
            case POPULARIDAD -> ingles
                    ? "Popular with travelers: average rating " + String.format(java.util.Locale.ROOT, "%.1f", item.calificacion()) + "."
                    : "Popular entre los turistas: calificación promedio " + String.format(java.util.Locale.ROOT, "%.1f", item.calificacion()) + ".";
            case DISPONIBILIDAD -> ingles
                    ? (item.esAtractivo() ? "Open now and available to visit." : "Published and available to book.")
                    : (item.esAtractivo() ? "Está abierto y disponible para visitar." : "Está publicado y disponible para reservar.");
        };
    }
}
