package co.edu.unimagdalena.samaria360.chatbot.domain.model;

import co.edu.unimagdalena.samaria360.chatbot.domain.exception.DatosInvalidosException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Itinerario guardado en el perfil del Turista (RF35; colección {@code itinerarios_sugeridos} del Entregable 2, §2.4):
 * nombre e ítems ordenados (atractivo o servicio del catálogo, con el título copiado para no consultar el catálogo al
 * mostrarlo, y el día sugerido). Se puede consultar y modificar sin regenerarlo. Límites de DEC-297: nombre de 1 a 100,
 * de 1 a 30 ítems, días de 1 a 7, sin repetir la misma referencia.
 */
public record Itinerario(String id, UUID turistaId, String nombre, List<Item> items, Instant fechaCreacion,
                         Instant fechaActualizacion) {

    public static final int NOMBRE_MAXIMO = 100;
    public static final int ITEMS_MAXIMOS = 30;
    public static final int TITULO_MAXIMO = 120;
    private static final Pattern OBJECT_ID = Pattern.compile("^[0-9a-f]{24}$");

    /** Un paso del itinerario: {@code tipo} es {@code atractivo} o {@code servicio}; {@code refId}, el id del catálogo. */
    public record Item(int orden, String tipo, String refId, String tituloActividad, int diaSugerido) {
    }

    public Itinerario {
        Objects.requireNonNull(turistaId, "turistaId");
        Objects.requireNonNull(nombre, "nombre");
        items = List.copyOf(items);
        Objects.requireNonNull(fechaCreacion, "fechaCreacion");
        Objects.requireNonNull(fechaActualizacion, "fechaActualizacion");
    }

    /**
     * Valida el nombre y los ítems y los renumera en el orden recibido (1, 2, 3…): el cliente decide el orden; el
     * servidor no confía en el {@code orden} que llegue.
     */
    public static Itinerario validar(String id, UUID turistaId, String nombre, List<Item> items, Instant creado,
                                     Instant ahora) {
        Map<String, String> campos = new LinkedHashMap<>();
        String limpio = nombre == null ? "" : nombre.strip();
        if (limpio.isEmpty()) {
            campos.put("nombre", "REQUERIDO");
        } else if (limpio.codePointCount(0, limpio.length()) > NOMBRE_MAXIMO) {
            campos.put("nombre", "LONGITUD_MAXIMA");
        }
        List<Item> recibidos = items == null ? List.of() : items;
        if (recibidos.isEmpty()) {
            campos.put("items", "REQUERIDO");
        } else if (recibidos.size() > ITEMS_MAXIMOS) {
            campos.put("items", "LONGITUD_MAXIMA");
        }
        List<Item> numerados = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < recibidos.size() && i < ITEMS_MAXIMOS; i++) {
            Item item = recibidos.get(i);
            String prefijo = "items[" + i + "].";
            if (item == null) {
                campos.put("items[" + i + "]", "REQUERIDO");
                continue;
            }
            if (!"atractivo".equals(item.tipo()) && !"servicio".equals(item.tipo())) {
                campos.put(prefijo + "tipo", "NO_PERMITIDO");
            }
            if (item.refId() == null || !OBJECT_ID.matcher(item.refId()).matches()) {
                campos.put(prefijo + "refId", "FORMATO");
            } else if (!vistos.add(item.tipo() + ":" + item.refId())) {
                campos.put(prefijo + "refId", "DUPLICADO");
            }
            String titulo = item.tituloActividad() == null ? "" : item.tituloActividad().strip();
            if (titulo.isEmpty()) {
                campos.put(prefijo + "tituloActividad", "REQUERIDO");
            } else if (titulo.codePointCount(0, titulo.length()) > TITULO_MAXIMO) {
                campos.put(prefijo + "tituloActividad", "LONGITUD_MAXIMA");
            }
            if (item.diaSugerido() < 1 || item.diaSugerido() > Interpretacion.DIAS_MAXIMOS) {
                campos.put(prefijo + "diaSugerido", "VALOR_FUERA_DE_RANGO");
            }
            numerados.add(new Item(i + 1, item.tipo(), item.refId(), titulo, item.diaSugerido()));
        }
        if (!campos.isEmpty()) {
            throw new DatosInvalidosException(campos);
        }
        return new Itinerario(id, turistaId, limpio, numerados, creado, ahora);
    }
}
