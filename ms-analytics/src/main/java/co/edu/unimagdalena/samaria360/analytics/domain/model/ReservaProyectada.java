package co.edu.unimagdalena.samaria360.analytics.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Hecho de una reserva, armado con los eventos de ms-booking (ADR-011; decisión 6 del equipo). Sirve a los tableros
 * (RF14 reservas diarias, RF31 por servicio) y a la elegibilidad de la calificación (RF17: solo con la reserva
 * Completada, decisión 1). Sin datos personales: solo identificadores.
 */
public record ReservaProyectada(UUID idReserva, UUID idTurista, UUID idPrestador, String idServicio,
                                String nombreServicio, Instant fechaInicio, int personas, EstadoReserva estado,
                                Instant actualizadaEn) {

    public enum EstadoReserva { Confirmada, Cancelada, Completada }

    public ReservaProyectada {
        Objects.requireNonNull(idReserva, "idReserva");
        Objects.requireNonNull(idTurista, "idTurista");
        Objects.requireNonNull(idPrestador, "idPrestador");
        Objects.requireNonNull(idServicio, "idServicio");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(actualizadaEn, "actualizadaEn");
    }

    public static ReservaProyectada de(EventoEntrante evento, EstadoReserva estado, Instant fechaInicio, int personas) {
        return new ReservaProyectada(evento.uuid("idReserva"), evento.uuid("idTurista"), evento.uuid("idPrestador"),
                evento.texto("idServicio"), evento.texto("nombreServicio"), fechaInicio, personas, estado,
                evento.ocurridoEn());
    }
}
