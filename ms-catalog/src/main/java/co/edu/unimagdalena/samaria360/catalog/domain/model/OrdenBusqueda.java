package co.edu.unimagdalena.samaria360.catalog.domain.model;

/**
 * Orden de los resultados de búsqueda (RF61). Por defecto: Distancia si hay ubicación; si no, Nombre.
 * {@code Calificacion}: el promedio visible que llega de ms-analytics (DEC-302), de mayor a menor; lo que no tiene
 * calificaciones va al final.
 */
public enum OrdenBusqueda {
    Distancia,
    PrecioAscendente,
    PrecioDescendente,
    Nombre,
    Calificacion
}
