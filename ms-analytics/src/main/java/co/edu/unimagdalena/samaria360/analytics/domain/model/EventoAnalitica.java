package co.edu.unimagdalena.samaria360.analytics.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Evento que ms-analytics publica en RabbitMQ (ADR-011; contrato en {@code docs/sad/api/eventos.md}). Sin datos
 * personales ni texto libre.
 */
public record EventoAnalitica(String tipo, String clave, Map<String, Object> datos, Instant ocurridoEn) {

    public EventoAnalitica {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(clave, "clave");
        Objects.requireNonNull(ocurridoEn, "ocurridoEn");
        datos = datos == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(datos));
    }

    /** {@code PromedioActualizado} (RF17, RF61): el promedio visible y su versión monótona por entidad. */
    public static EventoAnalitica promedioActualizado(PromedioCalificacion promedio, long version, Instant ahora) {
        if (version < 1) {
            throw new IllegalArgumentException("version desde 1");
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipoEntidad", promedio.tipoEntidad().name());
        datos.put("idEntidad", promedio.idEntidad());
        datos.put("promedio", promedio.promedio() == null ? null : promedio.promedio().doubleValue());
        datos.put("cantidad", promedio.cantidad());
        datos.put("version", version);
        return new EventoAnalitica("PromedioActualizado", promedio.idEntidad(), datos, ahora);
    }
}
