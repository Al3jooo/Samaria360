package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.out.persistence.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Forma común a la que la agregación proyecta servicios y atractivos (ADR-004). Es de la infraestructura: las
 * anotaciones de MongoDB no llegan al dominio.
 *
 * @param coordenadas GeoJSON {@code coordinates}: [longitud, latitud]
 * @param precio      Decimal128 en MongoDB ({@code precioBase}); nulo en atractivos
 */
public record ResultadoOfertaDocumento(
        @Id String id,
        String tipo,
        String nombre,
        String categoria,
        String descripcion,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal precio,
        String imagen,
        List<Double> coordenadas,
        Double distanciaMetros,
        List<String> condicionesAccesibilidad,
        String estadoOperativo,
        Double calificacion,
        Long cantidadCalificaciones,
        Integer ocupacion,
        java.time.Instant fechaOcupacion,
        Integer limite) {
}
