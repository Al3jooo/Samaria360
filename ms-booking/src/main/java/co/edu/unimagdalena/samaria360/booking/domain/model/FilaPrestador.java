package co.edu.unimagdalena.samaria360.booking.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Una fila del inventario vista por el Prestador (RF19; DEC-310): una noche de una unidad o un horario de un tour, con
 * sus cupos y si está cerrada. {@link #reservados()} son los cupos de reservas vigentes.
 */
public record FilaPrestador(CategoriaReservable categoria, String idUnidad, LocalDate fecha, LocalTime horaInicio,
                            int cuposTotales, int cuposDisponibles, boolean cerrado) {

    public FilaPrestador {
        Objects.requireNonNull(categoria, "categoria");
        Objects.requireNonNull(fecha, "fecha");
    }

    public int reservados() {
        return cuposTotales - cuposDisponibles;
    }
}
