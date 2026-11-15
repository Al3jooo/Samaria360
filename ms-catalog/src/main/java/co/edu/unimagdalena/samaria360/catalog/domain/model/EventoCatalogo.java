package co.edu.unimagdalena.samaria360.catalog.domain.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento que ms-catalog publica en RabbitMQ (ADR-011; contrato en {@code docs/sad/api/eventos.md}). {@code clave} es el
 * id del agregado; {@code datos}, los campos del contrato, sin datos personales ni texto libre (ADR-011, punto 7).
 */
public record EventoCatalogo(String tipo, String clave, Map<String, Object> datos, Instant ocurridoEn) {

    public EventoCatalogo {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(clave, "clave");
        Objects.requireNonNull(ocurridoEn, "ocurridoEn");
        datos = datos == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(datos));
    }

    /**
     * Aprobación o rechazo de un servicio (RF21), para la auditoría de RF13. La justificación del rechazo no viaja: es
     * texto libre. {@code nombreServicio} (DEC-323): el nombre comercial, para que la auditoría lo muestre (no es un dato
     * personal).
     */
    public static EventoCatalogo servicioDecidido(String idServicio, String nombreServicio, UUID prestadorId,
                                                  EstadoPublicacion estadoNuevo, UUID administrador, Instant ahora) {
        if (estadoNuevo != EstadoPublicacion.Publicado && estadoNuevo != EstadoPublicacion.Rechazado) {
            throw new IllegalArgumentException("Una decisión deja el servicio Publicado o Rechazado");
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idServicio", idServicio);
        if (nombreServicio != null && !nombreServicio.isBlank()) {
            datos.put("nombreServicio", nombreServicio);
        }
        datos.put("idPrestador", prestadorId.toString());
        datos.put("decision", estadoNuevo.name());
        datos.put("idAdministrador", administrador.toString());
        return new EventoCatalogo("ServicioDecidido", idServicio, datos, ahora);
    }

    /**
     * Ocupación registrada por el Gestor (RF39; DEC-304), para los reportes de afluencia de RF64 en ms-analytics. Sin
     * datos personales: ni el Gestor que la registró.
     */
    public static EventoCatalogo ocupacionRegistrada(AtractivoOcupacion atractivo, Afluencia afluencia) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idAtractivo", atractivo.id());
        datos.put("nombreAtractivo", atractivo.nombre());
        datos.put("categoria", atractivo.categoria());
        datos.put("ocupacion", afluencia.ocupacion());
        datos.put("limiteCapacidadCarga", afluencia.limite());
        datos.put("porcentaje", afluencia.porcentaje());
        datos.put("nivel", afluencia.nivel().name());
        return new EventoCatalogo("OcupacionRegistrada", atractivo.id(), datos, afluencia.fecha());
    }

    /**
     * Consultas a las fichas de una ventana (DEC-283): {@code conteos} con {@code tipoEntidad}, {@code idEntidad},
     * {@code nombre}, {@code idPrestador} (solo servicios) y {@code consultas}. Sin datos de quien consultó.
     */
    public static EventoCatalogo consultasAgregadas(Instant inicio, Instant fin, java.util.List<Map<String, Object>> conteos) {
        if (conteos.isEmpty() || conteos.size() > 1000) {
            throw new IllegalArgumentException("De 1 a 1000 conteos por evento");
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("ventanaInicio", inicio.toString());
        datos.put("ventanaFin", fin.toString());
        datos.put("conteos", java.util.List.copyOf(conteos));
        return new EventoCatalogo("ConsultasAgregadas", inicio.toString(), datos, fin);
    }
}
