package co.edu.unimagdalena.samaria360.analytics.application.service;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.TablerosRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.domain.exception.CamposInvalidosException;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Calificacion;
import co.edu.unimagdalena.samaria360.analytics.domain.model.ConsultasFicha;
import co.edu.unimagdalena.samaria360.analytics.domain.model.TipoEntidad;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * RF14, RF31 y RF64 (DEC-308). Valida los rangos (por defecto, los últimos 30 días hasta hoy en Santa Marta; como
 * máximo 366), completa con ceros los días sin reservas y une las consultas y las reservas de cada servicio.
 */
public class TablerosService implements TablerosUseCase {

    private final TablerosRepositoryPort tableros;
    private final RelojPort reloj;

    public TablerosService(TablerosRepositoryPort tableros, RelojPort reloj) {
        this.tableros = Objects.requireNonNull(tableros);
        this.reloj = Objects.requireNonNull(reloj);
    }

    private record Rango(LocalDate desde, LocalDate hasta) {
    }

    private Rango rango(String desde, String hasta) {
        Map<String, String> campos = new LinkedHashMap<>();
        LocalDate hoy = LocalDate.ofInstant(reloj.ahora(), ConsultasFicha.ZONA);
        LocalDate fin = dia(hasta, "hasta", campos, hoy);
        LocalDate inicio = dia(desde, "desde", campos, fin == null ? null : fin.minusDays(DIAS_POR_DEFECTO - 1L));
        if (campos.isEmpty()) {
            if (inicio.isAfter(fin)) {
                campos.put("hasta", "NO_PERMITIDO");
            } else if (ChronoUnit.DAYS.between(inicio, fin) + 1 > DIAS_MAXIMOS) {
                campos.put("desde", "VALOR_FUERA_DE_RANGO");
            }
        }
        if (!campos.isEmpty()) {
            throw new CamposInvalidosException(campos);
        }
        return new Rango(inicio, fin);
    }

    private static LocalDate dia(String texto, String campo, Map<String, String> campos, LocalDate porDefecto) {
        if (texto == null || texto.isBlank()) {
            return porDefecto;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            campos.put(campo, "FORMATO");
            return null;
        }
    }

    @Override
    public TableroGeneral general(String desde, String hasta) {
        Rango rango = rango(desde, hasta);
        Map<LocalDate, Long> porDia = tableros.reservasPorDia(rango.desde(), rango.hasta());
        List<ReservasDia> serie = new ArrayList<>();
        long total = 0;
        for (LocalDate dia = rango.desde(); !dia.isAfter(rango.hasta()); dia = dia.plusDays(1)) {
            long reservas = porDia.getOrDefault(dia, 0L);
            serie.add(new ReservasDia(dia, reservas));
            total += reservas;
        }
        return new TableroGeneral(rango.desde(), rango.hasta(), serie, total,
                tableros.masConsultadas(TipoEntidad.atractivo, rango.desde(), rango.hasta(), TOP_ATRACTIVOS),
                tableros.turistasPorPais());
    }

    @Override
    public PanelPrestador prestador(UUID prestador, Integer dias) {
        int periodo = dias == null ? 7 : dias;
        if (periodo != 7 && periodo != 30) {
            throw new CamposInvalidosException(Map.of("dias", "NO_PERMITIDO"));
        }
        LocalDate hasta = LocalDate.ofInstant(reloj.ahora(), ConsultasFicha.ZONA);
        LocalDate desde = hasta.minusDays(periodo - 1L);
        Map<String, ServicioPanel> servicios = new LinkedHashMap<>();
        for (EntidadConsultada c : tableros.consultasPorServicio(prestador, desde, hasta)) {
            servicios.put(c.id(), new ServicioPanel(c.id(), c.nombre(), c.consultas(), 0));
        }
        for (EntidadConsultada r : tableros.reservasPorServicio(prestador, desde, hasta)) {
            ServicioPanel actual = servicios.get(r.id());
            servicios.put(r.id(), actual == null ? new ServicioPanel(r.id(), r.nombre(), 0, r.consultas())
                    : new ServicioPanel(r.id(), actual.nombre() != null ? actual.nombre() : r.nombre(),
                    actual.consultas(), r.consultas()));
        }
        List<ServicioPanel> lista = new ArrayList<>(servicios.values());
        lista.sort(Comparator.comparingLong(ServicioPanel::consultas).reversed()
                .thenComparing(Comparator.comparingLong(ServicioPanel::reservas).reversed())
                .thenComparing(ServicioPanel::idServicio));
        return new PanelPrestador(periodo, desde, hasta, lista);
    }

    @Override
    public ReporteAfluencia afluencia(String desde, String hasta, String atractivo) {
        Rango rango = rango(desde, hasta);
        String filtro = atractivo == null || atractivo.isBlank() ? null : atractivo.trim();
        if (filtro != null && !Calificacion.esObjectId(filtro)) {
            throw new CamposInvalidosException(Map.of("atractivo", "FORMATO"));
        }
        return new ReporteAfluencia(rango.desde(), rango.hasta(), filtro,
                tableros.afluenciaPorAtractivo(rango.desde(), rango.hasta(), filtro),
                tableros.afluenciaPorDia(rango.desde(), rango.hasta(), filtro),
                tableros.atractivosConLecturas());
    }
}
