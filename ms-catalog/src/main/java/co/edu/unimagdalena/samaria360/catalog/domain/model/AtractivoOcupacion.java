package co.edu.unimagdalena.samaria360.catalog.domain.model;

import java.util.Objects;

/**
 * Lo que la afluencia necesita de un atractivo (RF39, RF08, RF51): su categoría y su punto (para los alternativos
 * «similares», DEC-305), su estado operativo, la imagen de la tarjeta y la última lectura de ocupación con el límite.
 */
public record AtractivoOcupacion(String id, String nombre, String categoria, String imagen, Coordenada coordenada,
                                 EstadoOperativo estadoOperativo, Afluencia.Lectura lectura) {

    public AtractivoOcupacion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(coordenada, "coordenada");
    }
}
