package co.edu.unimagdalena.samaria360.booking.domain.service;

import co.edu.unimagdalena.samaria360.booking.domain.exception.DatosReservaInvalidosException;
import co.edu.unimagdalena.samaria360.booking.domain.model.CategoriaReservable;
import co.edu.unimagdalena.samaria360.booking.domain.model.MotivoInvalido;
import co.edu.unimagdalena.samaria360.booking.domain.model.OfertaReservable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reglas de RF19 (decisión 3 del equipo; DEC-310; **decisión del equipo, no del SRS**): solo fechas futuras (desde
 * mañana en Santa Marta) dentro del horizonte materializado (60 días); de 1 a 10 000 cupos; en un tour, el horario; en
 * un alojamiento, una de sus unidades. La plantilla: de 1 a 7 días y, en un tour, de 1 a 10 salidas {@code HH:mm}.
 */
public class PoliticaCambioOferta {

    public static final int CUPOS_MAXIMOS = 10_000;
    public static final int SALIDAS_MAXIMAS = 10;

    /** La categoría reservable del servicio, o 400 si no se reserva (Gastronomía, Alquiler). */
    public CategoriaReservable categoria(OfertaReservable oferta) {
        try {
            return CategoriaReservable.valueOf(oferta.categoria());
        } catch (IllegalArgumentException e) {
            throw new DatosReservaInvalidosException(Map.of("idServicio", MotivoInvalido.CATEGORIA_NO_RESERVABLE));
        }
    }

    /** Fecha futura del horizonte, y el horario o la unidad según la categoría. */
    public void validarFila(OfertaReservable oferta, LocalDate fecha, LocalTime hora, String idUnidad, LocalDate hoy) {
        CategoriaReservable categoria = categoria(oferta);
        Map<String, MotivoInvalido> campos = new LinkedHashMap<>();
        if (fecha == null) {
            campos.put("fecha", MotivoInvalido.REQUERIDO);
        } else if (!fecha.isAfter(hoy)) {
            campos.put("fecha", MotivoInvalido.FECHA_PASADA);
        } else if (fecha.isAfter(MaterializacionOferta.ultimoDia(hoy))) {
            campos.put("fecha", MotivoInvalido.MAXIMO_DIAS);
        }
        if (categoria == CategoriaReservable.Tour) {
            if (hora == null) {
                campos.put("horaInicio", MotivoInvalido.REQUERIDO);
            }
            if (idUnidad != null) {
                campos.put("idUnidad", MotivoInvalido.NO_PERMITIDO);
            }
        } else {
            if (idUnidad == null) {
                campos.put("idUnidad", MotivoInvalido.REQUERIDO);
            } else if (oferta.unidad(idUnidad).isEmpty()) {
                campos.put("idUnidad", MotivoInvalido.NO_PERMITIDO);
            }
            if (hora != null) {
                campos.put("horaInicio", MotivoInvalido.NO_PERMITIDO);
            }
        }
        if (!campos.isEmpty()) {
            throw new DatosReservaInvalidosException(campos);
        }
    }

    public int cupos(Integer cupos) {
        if (cupos == null) {
            throw new DatosReservaInvalidosException(Map.of("cuposTotales", MotivoInvalido.REQUERIDO));
        }
        if (cupos < 1) {
            throw new DatosReservaInvalidosException(Map.of("cuposTotales", MotivoInvalido.VALOR_MINIMO));
        }
        if (cupos > CUPOS_MAXIMOS) {
            throw new DatosReservaInvalidosException(Map.of("cuposTotales", MotivoInvalido.MAXIMO_ELEMENTOS));
        }
        return cupos;
    }

    /** Plantilla validada: días en el orden de la semana y salidas ordenadas sin repetir (vacías en un alojamiento). */
    public record Plantilla(Set<DayOfWeek> dias, List<LocalTime> salidas) {
    }

    public Plantilla plantilla(OfertaReservable oferta, List<String> dias, List<String> salidas) {
        CategoriaReservable categoria = categoria(oferta);
        Map<String, MotivoInvalido> campos = new LinkedHashMap<>();
        Set<DayOfWeek> semana = EnumSet.noneOf(DayOfWeek.class);
        if (dias == null || dias.isEmpty()) {
            campos.put("diasOperacion", MotivoInvalido.REQUERIDO);
        } else {
            for (String dia : dias) {
                DayOfWeek d = DIAS.get(dia);
                if (d == null) {
                    campos.put("diasOperacion", MotivoInvalido.NO_PERMITIDO);
                } else {
                    semana.add(d);
                }
            }
        }
        List<LocalTime> horas = new ArrayList<>();
        if (categoria == CategoriaReservable.Tour) {
            if (salidas == null || salidas.isEmpty()) {
                campos.put("salidas", MotivoInvalido.REQUERIDO);
            } else if (salidas.size() > SALIDAS_MAXIMAS) {
                campos.put("salidas", MotivoInvalido.MAXIMO_ELEMENTOS);
            } else {
                for (String salida : salidas) {
                    try {
                        if (salida == null || !salida.matches("^[0-2][0-9]:[0-5][0-9]$")) {
                            throw new DateTimeParseException("HH:mm", String.valueOf(salida), 0);
                        }
                        LocalTime hora = LocalTime.parse(salida);
                        if (!horas.contains(hora)) {
                            horas.add(hora);
                        }
                    } catch (DateTimeParseException e) {
                        campos.put("salidas", MotivoInvalido.FORMATO);
                    }
                }
            }
        } else if (salidas != null && !salidas.isEmpty()) {
            campos.put("salidas", MotivoInvalido.NO_PERMITIDO);
        }
        if (!campos.isEmpty()) {
            throw new DatosReservaInvalidosException(campos);
        }
        horas.sort(null);
        return new Plantilla(semana, horas);
    }

    /** Los nombres del catálogo (sin tildes), en el orden de la semana. */
    public static final Map<String, DayOfWeek> DIAS = Map.of("Lunes", DayOfWeek.MONDAY, "Martes", DayOfWeek.TUESDAY,
            "Miercoles", DayOfWeek.WEDNESDAY, "Jueves", DayOfWeek.THURSDAY, "Viernes", DayOfWeek.FRIDAY,
            "Sabado", DayOfWeek.SATURDAY, "Domingo", DayOfWeek.SUNDAY);

    public static String nombre(DayOfWeek dia) {
        return DIAS.entrySet().stream().filter(e -> e.getValue() == dia).findFirst().orElseThrow().getKey();
    }
}
