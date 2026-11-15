package co.edu.unimagdalena.samaria360.chatbot.domain.model;

import java.util.Objects;

/**
 * Un resultado de la búsqueda pública de ms-catalog (DEC-280: sin endpoints nuevos ni copia del catálogo). {@code tipo}
 * es {@code Servicio} o {@code Atractivo}; {@code estadoOperativo}, solo en atractivos (RF33); {@code calificacion},
 * el promedio cuando el catálogo lo trae (RF61, iteración H), o nulo.
 */
public record ItemCatalogo(String tipo, String id, String nombre, String categoria, String descripcionCorta,
                           String estadoOperativo, Double calificacion) {

    public ItemCatalogo {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nombre, "nombre");
        descripcionCorta = descripcionCorta == null ? "" : descripcionCorta;
    }

    public boolean esAtractivo() {
        return "Atractivo".equals(tipo);
    }

    /** Un atractivo cerrado o en mantenimiento no se sugiere (RF33); un servicio publicado siempre está disponible. */
    public boolean disponible() {
        return !esAtractivo() || estadoOperativo == null || "Abierto".equals(estadoOperativo);
    }
}
