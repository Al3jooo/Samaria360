package co.edu.unimagdalena.samaria360.chatbot.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Lo que el asistente entiende de un mensaje para consultar el catálogo y la base de conocimientos (RF04): la categoría
 * de servicio de la búsqueda pública ({@code Alojamiento}, {@code Gastronomia}, {@code Tour} o {@code AlquilerEquipo}),
 * la categoría de atractivo ({@code Naturaleza} o {@code Cultura}), si pide un itinerario y para cuántos días (1 a 7), y
 * las palabras significativas.
 */
public record Interpretacion(String categoriaServicio, String categoriaAtractivo, boolean pideItinerario, int dias,
                             List<String> palabras) {

    public static final int DIAS_MAXIMOS = 7;

    public Interpretacion {
        if (dias < 1 || dias > DIAS_MAXIMOS) {
            throw new IllegalArgumentException("dias de 1 a " + DIAS_MAXIMOS);
        }
        palabras = List.copyOf(Objects.requireNonNullElse(palabras, List.of()));
    }

    public boolean buscaAlgo() {
        return categoriaServicio != null || categoriaAtractivo != null || pideItinerario || !palabras.isEmpty();
    }
}
