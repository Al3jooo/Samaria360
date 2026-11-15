package co.edu.unimagdalena.samaria360.analytics.application.port.in;

import java.util.List;

/** Una página de resultados; {@code pagina} desde 0. */
public record Pagina<T>(List<T> elementos, int pagina, int tamano, long totalElementos) {

    public Pagina {
        elementos = List.copyOf(elementos);
    }
}
