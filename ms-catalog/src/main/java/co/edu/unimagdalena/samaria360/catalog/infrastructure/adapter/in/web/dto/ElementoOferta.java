package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto;

import co.edu.unimagdalena.samaria360.catalog.domain.model.ResultadoOferta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Tarjeta de resultado y marcador del mapa (WF-05). La coordenada usa nombres explícitos para que el frontend no
 * dependa del orden GeoJSON [longitud, latitud].
 *
 * @param distanciaKm con dos decimales; nula si la búsqueda no tiene ubicación (RF38)
 * @param estadoOperativo solo en atractivos (RF33)
 * @param calificacion    promedio visible de RF17 (DEC-302); nulo sin calificaciones
 * @param cantidadCalificaciones calificaciones visibles; 0 sin calificaciones
 * @param afluencia       solo en atractivos: el nivel vigente {@code Bajo}, {@code Medio} o {@code Alto} (RF39, DEC-304);
 *                        nulo sin lectura del día
 */
public record ElementoOferta(String tipo, String id, String nombre, String categoria, String descripcionCorta,
                             BigDecimal precio, String imagen, CoordenadaDto coordenada, Double distanciaKm,
                             List<String> accesibilidad, String estadoOperativo, Double calificacion,
                             long cantidadCalificaciones, String afluencia) {

    public record CoordenadaDto(double longitud, double latitud) {
    }

    public static ElementoOferta de(ResultadoOferta resultado) {
        return new ElementoOferta(resultado.tipo().name(), resultado.id(), resultado.nombre(), resultado.categoria(),
                resultado.descripcionCorta(), resultado.precio(), resultado.imagenPrincipal(),
                new CoordenadaDto(resultado.coordenada().longitud(), resultado.coordenada().latitud()),
                resultado.distanciaKm() == null ? null
                        : BigDecimal.valueOf(resultado.distanciaKm()).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                resultado.accesibilidad().stream().map(Enum::name).sorted().toList(),
                resultado.estadoOperativo() == null ? null : resultado.estadoOperativo().name(),
                resultado.calificacion(),
                resultado.cantidadCalificaciones() == null ? 0 : resultado.cantidadCalificaciones(),
                resultado.afluencia() == null ? null : resultado.afluencia().nivel().name());
    }
}
