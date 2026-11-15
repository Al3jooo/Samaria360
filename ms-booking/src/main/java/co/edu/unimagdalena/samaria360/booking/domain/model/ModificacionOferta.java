package co.edu.unimagdalena.samaria360.booking.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Un cambio del Prestador en su oferta (RF19; DEC-310), que viaja como {@code OfertaModificada} a ms-catalog para el
 * historial de RF41 y, en una plantilla, para la oferta de las fechas que faltan materializar. Sin datos personales.
 *
 * @param tipo           {@code Cupos} (cambia los cupos de una fecha), {@code Apertura} (abre una fecha u horario que
 *                       no estaba o estaba cerrado), {@code Cierre} (cierra una fecha u horario sin reservas
 *                       confirmadas) o {@code Plantilla} (días de operación y salidas)
 * @param valorAnterior  texto del valor anterior (cupos o plantilla); nulo en una apertura nueva
 * @param valorNuevo     texto del valor nuevo
 */
public record ModificacionOferta(String idServicio, UUID idPrestador, Tipo tipo, LocalDate fecha, LocalTime horaInicio,
                                 String idUnidad, String valorAnterior, String valorNuevo, List<String> diasOperacion,
                                 List<String> salidas, Instant ocurridoEn) {

    public enum Tipo { Cupos, Apertura, Cierre, Plantilla }

    public ModificacionOferta {
        Objects.requireNonNull(idServicio, "idServicio");
        Objects.requireNonNull(idPrestador, "idPrestador");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(valorNuevo, "valorNuevo");
        Objects.requireNonNull(ocurridoEn, "ocurridoEn");
        diasOperacion = diasOperacion == null ? null : List.copyOf(diasOperacion);
        salidas = salidas == null ? null : List.copyOf(salidas);
    }

    /** La carga del evento ({@code eventos.md}, {@code OfertaModificada}). */
    public Map<String, Object> datos() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idServicio", idServicio);
        datos.put("idPrestador", idPrestador.toString());
        datos.put("tipo", tipo.name());
        if (fecha != null) {
            datos.put("fecha", fecha.toString());
        }
        if (horaInicio != null) {
            datos.put("horaInicio", horaInicio.toString());
        }
        if (idUnidad != null) {
            datos.put("idUnidad", idUnidad);
        }
        if (valorAnterior != null) {
            datos.put("valorAnterior", valorAnterior);
        }
        datos.put("valorNuevo", valorNuevo);
        if (diasOperacion != null) {
            datos.put("diasOperacion", diasOperacion);
        }
        if (salidas != null) {
            datos.put("salidas", salidas);
        }
        return datos;
    }
}
