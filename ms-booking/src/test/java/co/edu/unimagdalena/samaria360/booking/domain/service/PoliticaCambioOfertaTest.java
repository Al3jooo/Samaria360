package co.edu.unimagdalena.samaria360.booking.domain.service;

import co.edu.unimagdalena.samaria360.booking.application.port.in.ConsultarAsistentesUseCase.ListadoAsistentes;
import co.edu.unimagdalena.samaria360.booking.application.service.ConsultarAsistentesService;
import co.edu.unimagdalena.samaria360.booking.domain.exception.DatosReservaInvalidosException;
import co.edu.unimagdalena.samaria360.booking.domain.model.Asistente;
import co.edu.unimagdalena.samaria360.booking.domain.model.ContactoTurista;
import co.edu.unimagdalena.samaria360.booking.domain.model.ModificacionOferta;
import co.edu.unimagdalena.samaria360.booking.domain.model.MotivoInvalido;
import co.edu.unimagdalena.samaria360.booking.domain.model.OfertaOperativa;
import co.edu.unimagdalena.samaria360.booking.domain.model.OfertaReservable;
import co.edu.unimagdalena.samaria360.booking.domain.model.UnidadReservable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas de RF19 (decisión 3 del equipo, DEC-310) y el armado del listado de RF42 con dobles (DEC-312). */
class PoliticaCambioOfertaTest {

    private static final LocalDate HOY = LocalDate.of(2030, 3, 10);
    private static final PoliticaCambioOferta POLITICA = new PoliticaCambioOferta();

    private static OfertaReservable tour() {
        return new OfertaReservable("6a0000000000000000000001", UUID.randomUUID(), "Tour", "Tour", new BigDecimal("50000"),
                10, BigDecimal.ONE, List.of(), new OfertaOperativa(Set.of(DayOfWeek.MONDAY), List.of(LocalTime.of(8, 0))));
    }

    private static OfertaReservable hotel() {
        return new OfertaReservable("6a0000000000000000000002", UUID.randomUUID(), "Hotel", "Alojamiento",
                new BigDecimal("100000"), null, null,
                List.of(new UnidadReservable("u1", "Doble", 2, 2, new BigDecimal("100000"))),
                new OfertaOperativa(Set.of(DayOfWeek.MONDAY), List.of()));
    }

    private static Map<String, MotivoInvalido> campos(Runnable accion) {
        try {
            accion.run();
        } catch (DatosReservaInvalidosException e) {
            return e.getCampos();
        }
        throw new AssertionError("Se esperaba DATOS_INVALIDOS");
    }

    @Test
    void soloFechasFuturasDelHorizonteConLaFormaDeLaCategoria() {
        POLITICA.validarFila(tour(), HOY.plusDays(1), LocalTime.of(8, 0), null, HOY);
        POLITICA.validarFila(hotel(), HOY.plusDays(59), null, "u1", HOY);

        assertThat(campos(() -> POLITICA.validarFila(tour(), HOY, null, "u1", HOY)))
                .containsEntry("fecha", MotivoInvalido.FECHA_PASADA).containsEntry("horaInicio", MotivoInvalido.REQUERIDO)
                .containsEntry("idUnidad", MotivoInvalido.NO_PERMITIDO);
        assertThat(campos(() -> POLITICA.validarFila(hotel(), HOY.plusDays(60), null, "otra", HOY)))
                .containsEntry("fecha", MotivoInvalido.MAXIMO_DIAS).containsEntry("idUnidad", MotivoInvalido.NO_PERMITIDO);
        assertThatThrownBy(() -> POLITICA.cupos(0)).isInstanceOf(DatosReservaInvalidosException.class);
        assertThatThrownBy(() -> POLITICA.cupos(10_001)).isInstanceOf(DatosReservaInvalidosException.class);
    }

    @Test
    void laPlantillaOrdenaYValida() {
        PoliticaCambioOferta.Plantilla p = POLITICA.plantilla(tour(), List.of("Domingo", "Lunes"), List.of("14:00", "08:00", "08:00"));

        assertThat(p.dias()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.SUNDAY);
        assertThat(p.salidas()).containsExactly(LocalTime.of(8, 0), LocalTime.of(14, 0));
        assertThat(campos(() -> POLITICA.plantilla(hotel(), List.of(), List.of("08:00"))))
                .containsEntry("diasOperacion", MotivoInvalido.REQUERIDO).containsEntry("salidas", MotivoInvalido.NO_PERMITIDO);
        assertThat(campos(() -> POLITICA.plantilla(tour(), List.of("Lunes"), List.of("8:00"))))
                .containsEntry("salidas", MotivoInvalido.FORMATO);
    }

    @Test
    void elEventoNoLlevaDatosPersonales() {
        ModificacionOferta m = new ModificacionOferta("6a0000000000000000000001", UUID.randomUUID(),
                ModificacionOferta.Tipo.Cupos, HOY, LocalTime.of(8, 0), null, "10", "5", null, null, Instant.EPOCH);

        assertThat(m.datos()).containsEntry("tipo", "Cupos").containsEntry("horaInicio", "08:00")
                .containsEntry("valorAnterior", "10").doesNotContainKey("diasOperacion");
    }

    @Test
    void losAsistentesLlevanElContactoYElTotalDePersonas() {
        UUID ana = UUID.randomUUID();
        UUID luis = UUID.randomUUID();
        ConsultarAsistentesService servicio = new ConsultarAsistentesService(
                (prestador, idServicio, fecha, hora) -> List.of(
                        new Asistente(UUID.randomUUID(), luis, null, null, null, 1, LocalTime.of(8, 0), null, fecha, null),
                        new Asistente(UUID.randomUUID(), ana, null, null, null, 2, LocalTime.of(8, 0), null, fecha, null)),
                turistas -> Map.of(ana, new ContactoTurista(ana, "Ana", "ana@x.co", null),
                        luis, new ContactoTurista(luis, "Luis", "luis@x.co", "300")));

        ListadoAsistentes listado = servicio.asistentes(UUID.randomUUID(), "6a0000000000000000000001", HOY, null);

        assertThat(listado.totalPersonas()).isEqualTo(3);
        assertThat(listado.asistentes()).extracting(Asistente::nombre).containsExactly("Ana", "Luis");
        assertThat(listado.asistentes().get(0).toString()).doesNotContain("ana@x.co");
        assertThatThrownBy(() -> servicio.asistentes(UUID.randomUUID(), "x", null, null))
                .isInstanceOf(DatosReservaInvalidosException.class);
    }
}
