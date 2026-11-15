package co.edu.unimagdalena.samaria360.booking.application.port.out;

import co.edu.unimagdalena.samaria360.booking.domain.model.CategoriaReservable;
import co.edu.unimagdalena.samaria360.booking.domain.model.FilaPrestador;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * El inventario visto y modificado por el Prestador (RF19; V8, DEC-310). Cada cambio es una sentencia condicional
 * sobre una fila, dentro de la transacción del caso de uso, con el evento del outbox.
 */
public interface InventarioPrestadorPort {

    /** Las filas del rango, también las cerradas. */
    List<FilaPrestador> listar(String idServicio, LocalDate desde, LocalDate hasta);

    Optional<FilaPrestador> buscar(String idServicio, String idUnidad, LocalDate fecha, LocalTime hora);

    /**
     * Fija los cupos totales, ajusta los disponibles en la misma diferencia y abre la fila si estaba cerrada, solo si
     * {@code nuevo} no baja de lo reservado. {@code false} si no se cumplió (no cambia nada).
     */
    boolean ajustar(String idServicio, String idUnidad, LocalDate fecha, LocalTime hora, int nuevo);

    /** Abre una fecha u horario que no existía. {@code false} si ya existía. */
    boolean insertar(String idServicio, CategoriaReservable categoria, String idUnidad, LocalDate fecha,
                     LocalTime hora, int cupos);

    /** Cierra la fila solo si está abierta y sin cupos reservados. {@code false} si no se cumplió. */
    boolean cerrar(String idServicio, String idUnidad, LocalDate fecha, LocalTime hora);
}
