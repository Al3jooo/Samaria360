package co.edu.unimagdalena.samaria360.analytics.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tableros y reportes (iteración K; DEC-308): RF14 (Administrador y Gestor, respuesta 4 del equipo), RF31 (Prestador,
 * 7 o 30 días) y RF64 (Gestor). Se arman con las vistas que alimentan los eventos (decisión 6 del equipo): ms-analytics
 * no lee bases ajenas. Los días son de Santa Marta.
 */
public interface TablerosUseCase {

    int DIAS_POR_DEFECTO = 30;
    int DIAS_MAXIMOS = 366;
    int TOP_ATRACTIVOS = 3;

    record ReservasDia(LocalDate dia, long reservas) {
    }

    record EntidadConsultada(String id, String nombre, long consultas) {
    }

    record VisitantesZona(String pais, long turistas) {
    }

    /** RF14: reservas por día del rango, el top 3 de atractivos más consultados y los turistas por país. */
    record TableroGeneral(LocalDate desde, LocalDate hasta, List<ReservasDia> reservasPorDia, long totalReservas,
                          List<EntidadConsultada> atractivosMasConsultados, List<VisitantesZona> visitantesPorZona) {
    }

    record ServicioPanel(String idServicio, String nombre, long consultas, long reservas) {
    }

    /** RF31: por cada servicio del Prestador, las consultas a su ficha y las reservas recibidas en el periodo. */
    record PanelPrestador(int dias, LocalDate desde, LocalDate hasta, List<ServicioPanel> servicios) {
    }

    record AfluenciaAtractivo(String idAtractivo, String nombre, long lecturas, BigDecimal porcentajePromedio,
                              int porcentajeMaximo, String ultimoNivel, Instant ultimaLectura) {
    }

    record AfluenciaDia(LocalDate dia, BigDecimal porcentajePromedio, int porcentajeMaximo, long lecturas) {
    }

    record AtractivoReporte(String idAtractivo, String nombre) {
    }

    /** RF64: la afluencia de los atractivos en el rango, por atractivo y por día, con el filtro de atractivo. */
    record ReporteAfluencia(LocalDate desde, LocalDate hasta, String atractivo, List<AfluenciaAtractivo> atractivos,
                            List<AfluenciaDia> porDia, List<AtractivoReporte> atractivosDisponibles) {
    }

    /** {@code desde} y {@code hasta} opcionales (por defecto, los últimos 30 días); como máximo 366 días. */
    TableroGeneral general(String desde, String hasta);

    /** {@code dias}: 7 o 30 (RF31). */
    PanelPrestador prestador(UUID prestador, Integer dias);

    ReporteAfluencia afluencia(String desde, String hasta, String atractivo);
}
