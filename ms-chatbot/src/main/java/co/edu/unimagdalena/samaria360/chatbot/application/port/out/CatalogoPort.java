package co.edu.unimagdalena.samaria360.chatbot.application.port.out;

import co.edu.unimagdalena.samaria360.chatbot.domain.model.ItemCatalogo;

import java.util.List;

/**
 * La «herramienta» del asistente (decisión 5 del equipo): la búsqueda pública de ms-catalog
 * ({@code GET /api/v1/catalogo/oferta}), sin token y sin endpoints nuevos (DEC-280).
 */
public interface CatalogoPort {

    /**
     * @param texto     palabra a buscar en el nombre o la descripción, o nulo
     * @param categoria categoría de servicio de la búsqueda, o nula (servicios y atractivos)
     * @throws CatalogoNoDisponibleException si ms-catalog no responde
     */
    List<ItemCatalogo> buscar(String texto, String categoria, int tamano);
}
