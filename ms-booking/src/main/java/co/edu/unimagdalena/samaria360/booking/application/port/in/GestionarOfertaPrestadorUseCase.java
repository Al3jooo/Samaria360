package co.edu.unimagdalena.samaria360.booking.application.port.in;

import co.edu.unimagdalena.samaria360.booking.domain.model.FilaPrestador;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * RF19 (decisión 3 del equipo, respuesta 3; DEC-310): el Prestador ve y modifica las fechas, los horarios y los cupos
 * de su servicio en ms-booking, que valida de forma atómica y publica {@code OfertaModificada}.
 */
public interface GestionarOfertaPrestadorUseCase {

    record Inventario(String idServicio, String nombre, String categoria, LocalDate desde, LocalDate hasta,
                      List<String> diasOperacion, List<String> salidas, List<FilaPrestador> filas) {
    }

    record ComandoFila(UUID prestador, String idServicio, LocalDate fecha, LocalTime horaInicio, String idUnidad,
                       Integer cuposTotales) {
    }

    record ComandoPlantilla(UUID prestador, String idServicio, List<String> diasOperacion, List<String> salidas) {
    }

    Inventario inventario(UUID prestador, String idServicio, LocalDate desde, LocalDate hasta);

    /** Fija los cupos de una fecha u horario (lo abre si no estaba o estaba cerrado). */
    FilaPrestador ajustar(ComandoFila comando);

    /** Cierra una fecha u horario sin reservas confirmadas. */
    FilaPrestador cerrar(ComandoFila comando);

    /** Cambia la plantilla de las fechas que faltan materializar (la aplica ms-catalog en la oferta). */
    void cambiarPlantilla(ComandoPlantilla comando);
}
