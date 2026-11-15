package co.edu.unimagdalena.samaria360.auth.domain.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Decisión del Administrador sobre un prestador (RF28), para la auditoría de RF13. La justificación del rechazo no viaja:
 * es texto libre (ADR-011, punto 7).
 */
public record EventoPrestadorValidado(UUID idPrestador, boolean aprobado, UUID idAdministrador, Instant ocurridoEn)
        implements EventoPublicable {

    public EventoPrestadorValidado {
        Objects.requireNonNull(idPrestador, "idPrestador");
        Objects.requireNonNull(idAdministrador, "idAdministrador");
        Objects.requireNonNull(ocurridoEn, "ocurridoEn");
    }

    @Override
    public String tipo() {
        return "PrestadorValidado";
    }

    @Override
    public UUID idUsuario() {
        return idPrestador;
    }

    @Override
    public Map<String, Object> datos() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idPrestador", idPrestador.toString());
        datos.put("decision", aprobado ? "Aprobado" : "Rechazado");
        datos.put("idAdministrador", idAdministrador.toString());
        return datos;
    }
}
