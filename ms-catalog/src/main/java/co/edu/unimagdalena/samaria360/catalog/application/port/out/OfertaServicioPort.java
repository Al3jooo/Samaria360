package co.edu.unimagdalena.samaria360.catalog.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * La oferta y la galería de un servicio fuera de la edición con versión (RF19 plantilla; RF48 galería; DEC-311,
 * DEC-313). Escrituras condicionales de un documento; no suben la {@code version} de la edición del Prestador.
 */
public interface OfertaServicioPort {

    /** Fija {@code oferta.diasOperacion} y {@code oferta.salidas} (plantilla de RF19). {@code false} si no existe. */
    boolean fijarOferta(String idServicio, UUID prestador, List<String> diasOperacion, List<String> salidas);

    /** Agrega la ruta solo si el servicio está Publicado y tiene menos de 5 imágenes. */
    boolean agregarImagen(String idServicio, UUID prestador, String ruta);

    /** Quita la ruta solo si el servicio está Publicado, la tiene y le queda al menos 1. */
    boolean quitarImagen(String idServicio, UUID prestador, String ruta);
}
