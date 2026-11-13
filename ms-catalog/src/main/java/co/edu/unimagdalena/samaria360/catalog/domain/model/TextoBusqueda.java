package co.edu.unimagdalena.samaria360.catalog.domain.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Comparación del texto de búsqueda sin distinguir mayúsculas ni tildes (DEC-274; punto 5 de «Pendientes para el
 * SAD»): «cafe» encuentra «Café» y «café» encuentra «Cafe». Una sola regla para {@code PoliticaBusqueda} y para la
 * consulta de MongoDB, que la expresa con {@link #patronSinTildes}: así no hace falta guardar campos normalizados ni
 * migrar el esquema. La «ñ» se compara como «n» (al buscar, «pina» encuentra «piña»).
 */
public final class TextoBusqueda {

    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final Pattern METACARACTERES = Pattern.compile("[\\\\^$.|?*+()\\[\\]{}]");

    /** Cada letra base y las variantes que el patrón acepta en su lugar (minúsculas y mayúsculas). */
    private static final Map<Character, String> VARIANTES = Map.of(
            'a', "aáàäâãAÁÀÄÂÃ",
            'e', "eéèëêEÉÈËÊ",
            'i', "iíìïîIÍÌÏÎ",
            'o', "oóòöôõOÓÒÖÔÕ",
            'u', "uúùüûUÚÙÜÛ",
            'n', "nñNÑ",
            'c', "cçCÇ");

    private TextoBusqueda() {
    }

    /** Sin tildes ni diéresis y en minúsculas: «Café Ñandú» → «cafe nandu». */
    public static String normalizar(String texto) {
        String descompuesto = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return MARCAS.matcher(descompuesto).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** {@code true} si {@code texto} contiene {@code buscado}, sin distinguir mayúsculas ni tildes. */
    public static boolean contiene(String texto, String buscado) {
        return texto != null && normalizar(texto).contains(normalizar(buscado));
    }

    /**
     * Expresión regular (para usar con la opción {@code i}) que encuentra {@code buscado} sin distinguir tildes: cada
     * vocal, «n» y «c» pasan a una clase con sus variantes, y el resto de caracteres se escapa como literal.
     */
    public static String patronSinTildes(String buscado) {
        StringBuilder patron = new StringBuilder();
        for (char letra : normalizar(buscado).toCharArray()) {
            String variantes = VARIANTES.get(letra);
            if (variantes != null) {
                patron.append('[').append(variantes).append(']');
            } else {
                patron.append(METACARACTERES.matcher(String.valueOf(letra)).replaceAll("\\\\$0"));
            }
        }
        return patron.toString();
    }
}
