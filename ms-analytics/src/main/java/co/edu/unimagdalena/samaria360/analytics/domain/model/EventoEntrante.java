package co.edu.unimagdalena.samaria360.analytics.domain.model;

import co.edu.unimagdalena.samaria360.analytics.domain.exception.EventoInvalidoException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Evento recibido de RabbitMQ con el sobre de {@code docs/sad/api/eventos.md} (ADR-011). {@code datos} llega como un
 * mapa: los métodos de lectura validan cada campo y lanzan {@link EventoInvalidoException} si falta o no tiene la forma
 * del contrato (el mensaje termina en la cola de mensajes fallidos).
 */
public record EventoEntrante(UUID idEvento, String tipo, String origen, Instant ocurridoEn, String clave,
                             Map<String, Object> datos) {

    public EventoEntrante {
        Objects.requireNonNull(idEvento, "idEvento");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(ocurridoEn, "ocurridoEn");
        // Sin Map.copyOf: un campo del contrato puede ser null (idUnidad en un Tour).
        datos = datos == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(datos));
    }

    public String texto(String campo) {
        Object valor = datos.get(campo);
        if (!(valor instanceof String texto) || texto.isBlank()) {
            throw new EventoInvalidoException(tipo + ": falta el texto " + campo);
        }
        return texto;
    }

    public Optional<String> textoOpcional(String campo) {
        Object valor = datos.get(campo);
        return valor instanceof String texto && !texto.isBlank() ? Optional.of(texto) : Optional.empty();
    }

    public UUID uuid(String campo) {
        try {
            return UUID.fromString(texto(campo));
        } catch (IllegalArgumentException e) {
            throw new EventoInvalidoException(tipo + ": " + campo + " no es un UUID");
        }
    }

    public Instant instante(String campo) {
        try {
            return Instant.parse(texto(campo));
        } catch (DateTimeParseException e) {
            throw new EventoInvalidoException(tipo + ": " + campo + " no es un instante ISO 8601");
        }
    }

    public long entero(String campo) {
        Object valor = datos.get(campo);
        if (!(valor instanceof Number numero)) {
            throw new EventoInvalidoException(tipo + ": falta el número " + campo);
        }
        return numero.longValue();
    }
}
