package co.edu.unimagdalena.samaria360.booking.domain.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Hecho de una reserva que ms-booking publica en RabbitMQ (ADR-011; contrato en {@code docs/sad/api/eventos.md}): la
 * confirmación (RF20), la cancelación (RF18) y el paso a Completada (RF52). Sin datos personales: solo identificadores,
 * el nombre del servicio (dato comercial público), las fechas y las personas (ADR-011, punto 7).
 */
public record EventoReserva(Tipo tipo, Reserva reserva, UUID canceladaPor, Instant ocurridoEn) {

    public enum Tipo {
        ReservaConfirmada,
        ReservaCancelada,
        ReservaCompletada
    }

    public EventoReserva {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(reserva, "reserva");
        Objects.requireNonNull(ocurridoEn, "ocurridoEn");
        if (tipo == Tipo.ReservaCancelada && canceladaPor == null) {
            throw new IllegalArgumentException("La cancelación lleva quién la hizo (RF13)");
        }
    }

    public static EventoReserva confirmada(Reserva reserva, Instant ahora) {
        return new EventoReserva(Tipo.ReservaConfirmada, reserva, null, ahora);
    }

    public static EventoReserva cancelada(Reserva reserva, UUID canceladaPor, Instant ahora) {
        return new EventoReserva(Tipo.ReservaCancelada, reserva, canceladaPor, ahora);
    }

    public static EventoReserva completada(Reserva reserva, Instant ahora) {
        return new EventoReserva(Tipo.ReservaCompletada, reserva, null, ahora);
    }

    /** Campos de {@code datos}, en el orden del contrato. */
    public Map<String, Object> datos() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idReserva", reserva.idReserva().toString());
        datos.put("idTurista", reserva.idTurista().toString());
        datos.put("idPrestador", reserva.idPrestador().toString());
        datos.put("idServicio", reserva.idServicio());
        datos.put("nombreServicio", reserva.nombreServicio());
        switch (tipo) {
            case ReservaConfirmada -> {
                datos.put("categoria", reserva.categoria().name());
                datos.put("idUnidad", reserva.idUnidad());
                datos.put("fechaInicio", reserva.fechaInicio().toString());
                datos.put("fechaFin", reserva.fechaFin().toString());
                datos.put("personas", reserva.personas());
                datos.put("cantidad", reserva.cantidad());
            }
            case ReservaCancelada -> {
                datos.put("fechaInicio", reserva.fechaInicio().toString());
                datos.put("personas", reserva.personas());
                datos.put("canceladaPor", canceladaPor.toString());
            }
            case ReservaCompletada -> datos.put("fechaFin", reserva.fechaFin().toString());
        }
        return datos;
    }
}
