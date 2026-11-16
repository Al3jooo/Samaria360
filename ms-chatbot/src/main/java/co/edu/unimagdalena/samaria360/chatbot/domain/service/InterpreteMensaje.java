package co.edu.unimagdalena.samaria360.chatbot.domain.service;

import co.edu.unimagdalena.samaria360.chatbot.domain.model.Interpretacion;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpreta un mensaje en español o inglés (RF04, RF59) para consultar el catálogo y la base de conocimientos, sin
 * modelo de lenguaje (decisión 5 del equipo: la consulta no depende del LLM). Reconoce la categoría por palabras clave,
 * si pide un itinerario y los días («3 días», «2 days»), y deja las palabras significativas.
 */
public class InterpreteMensaje {

    private static final Map<String, String> SERVICIOS = new LinkedHashMap<>();
    private static final Map<String, String> ATRACTIVOS = new LinkedHashMap<>();
    private static final Set<String> ITINERARIO = Set.of("itinerario", "itinerary", "plan", "planear", "ruta", "route",
            "recorrido", "agenda", "dias", "days", "fin de semana", "weekend", "visitar", "visit");
    private static final Set<String> VACIAS = Set.of("el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del",
            "en", "y", "o", "a", "al", "que", "para", "por", "con", "mi", "me", "quiero", "busco", "hay", "donde", "como",
            "cual", "cuales", "santa", "marta", "the", "an", "of", "in", "and", "or", "to", "for", "with", "my", "i", "want",
            "is", "are", "there", "where", "what", "which", "how", "some", "dame", "recomienda", "recomiendame",
            "recommend", "show", "muestrame", "puedes", "can", "you", "please", "favor", "hola", "hello", "hi");
    private static final Map<String, Integer> NUMEROS = Map.ofEntries(
            Map.entry("un", 1), Map.entry("uno", 1), Map.entry("una", 1), Map.entry("one", 1),
            Map.entry("dos", 2), Map.entry("two", 2), Map.entry("tres", 3), Map.entry("three", 3),
            Map.entry("cuatro", 4), Map.entry("four", 4), Map.entry("cinco", 5), Map.entry("five", 5),
            Map.entry("seis", 6), Map.entry("six", 6), Map.entry("siete", 7), Map.entry("seven", 7),
            Map.entry("ocho", 8), Map.entry("eight", 8), Map.entry("nueve", 9), Map.entry("nine", 9),
            Map.entry("diez", 10), Map.entry("ten", 10));
    private static final Pattern DIAS = Pattern.compile(
            "(?<![a-z0-9])(\\d{1,2}|" + String.join("|", NUMEROS.keySet()) + ")[\\s-]*(?:dias?|days?)\\b");

    static {
        for (String clave : List.of("alojamiento", "hotel", "hostal", "hospedaje", "dormir", "habitacion", "lodging",
                "accommodation", "stay", "hostel", "posada")) {
            SERVICIOS.put(clave, "Alojamiento");
        }
        for (String clave : List.of("restaurante", "comida", "comer", "gastronomia", "almuerzo", "cena", "restaurant",
                "food", "eat", "lunch", "dinner", "cafe")) {
            SERVICIOS.put(clave, "Gastronomia");
        }
        for (String clave : List.of("tour", "tours", "excursion", "caminata", "guia", "trek", "hike", "hiking")) {
            SERVICIOS.put(clave, "Tour");
        }
        for (String clave : List.of("alquiler", "alquilar", "rentar", "equipo", "equipos", "bicicleta", "kayak", "rent",
                "rental", "bike", "equipment")) {
            SERVICIOS.put(clave, "AlquilerEquipo");
        }
        for (String clave : List.of("naturaleza", "playa", "playas", "parque", "sendero", "montana", "sierra", "nature",
                "beach", "beaches", "park", "trail", "mountain")) {
            ATRACTIVOS.put(clave, "Naturaleza");
        }
        for (String clave : List.of("cultura", "museo", "historia", "historico", "catedral", "cultural", "culture",
                "museum", "history", "historic")) {
            ATRACTIVOS.put(clave, "Cultura");
        }
    }

    public Interpretacion interpretar(String mensaje) {
        String normal = normalizar(mensaje);
        String categoriaServicio = null;
        String categoriaAtractivo = null;
        boolean itinerario = false;
        List<String> palabras = new ArrayList<>();
        // La duración es una instrucción, no un término para buscar lugares en el catálogo.
        for (String palabra : DIAS.matcher(normal).replaceAll(" ").split("[^a-z0-9]+")) {
            if (palabra.isBlank()) {
                continue;
            }
            if (categoriaServicio == null && SERVICIOS.containsKey(palabra)) {
                categoriaServicio = SERVICIOS.get(palabra);
            } else if (categoriaAtractivo == null && ATRACTIVOS.containsKey(palabra)) {
                categoriaAtractivo = ATRACTIVOS.get(palabra);
            } else if (ITINERARIO.contains(palabra)) {
                itinerario = true;
            } else if (!VACIAS.contains(palabra) && palabra.length() > 2 && !palabra.chars().allMatch(Character::isDigit)) {
                palabras.add(palabra);
            }
        }
        if (normal.contains("fin de semana") || normal.contains("weekend")) {
            itinerario = true;
        }
        int dias = 1;
        Matcher coincidencia = DIAS.matcher(normal);
        if (coincidencia.find()) {
            itinerario = true;
            String numero = coincidencia.group(1);
            int cantidad = NUMEROS.containsKey(numero) ? NUMEROS.get(numero) : Integer.parseInt(numero);
            dias = Math.max(1, Math.min(Interpretacion.DIAS_MAXIMOS, cantidad));
        } else if (normal.contains("fin de semana") || normal.contains("weekend")) {
            dias = 2;
        }
        return new Interpretacion(categoriaServicio, categoriaAtractivo, itinerario, dias,
                palabras.size() > 5 ? palabras.subList(0, 5) : palabras);
    }

    /** Minúsculas y sin tildes, como compara la búsqueda pública (DEC-274). */
    static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }
}
