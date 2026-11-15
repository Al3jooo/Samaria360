package co.edu.unimagdalena.samaria360.booking.application.service;

import co.edu.unimagdalena.samaria360.booking.application.fake.OutboxEventosEnMemoria;
import co.edu.unimagdalena.samaria360.booking.application.port.out.PublicacionFallidaException;
import co.edu.unimagdalena.samaria360.booking.application.port.out.PublicacionPendiente;
import co.edu.unimagdalena.samaria360.booking.domain.model.CategoriaReservable;
import co.edu.unimagdalena.samaria360.booking.domain.model.EstadoReserva;
import co.edu.unimagdalena.samaria360.booking.domain.model.EventoReserva;
import co.edu.unimagdalena.samaria360.booking.domain.model.Reserva;
import co.edu.unimagdalena.samaria360.booking.domain.model.UnidadCobro;
import co.edu.unimagdalena.samaria360.booking.domain.service.PoliticaReintento;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Relevo del outbox de reservas hacia RabbitMQ (ADR-011, DEC-291) con dobles. */
public class PublicarEventosServiceTest {

    public static final Instant T0 = Instant.parse("2030-03-01T15:00:00Z");

    public static Reserva reserva() {
        return new Reserva(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "65f0c0ffee0000000000000a",
                "Tour a Playa Cristal", CategoriaReservable.Tour, null, null, EstadoReserva.Confirmada, 4, 4,
                new BigDecimal("80000"), UnidadCobro.values()[0], new BigDecimal("320000"), "COP",
                LocalDate.of(2030, 3, 10), LocalDate.of(2030, 3, 10), LocalTime.of(8, 0),
                Instant.parse("2030-03-10T13:00:00Z"), Instant.parse("2030-03-10T18:00:00Z"), T0, null,
                UUID.randomUUID(), "hash");
    }

    private final OutboxEventosEnMemoria outbox = new OutboxEventosEnMemoria();
    private final List<PublicacionPendiente> publicadas = new ArrayList<>();
    private boolean caido;
    private final PublicarEventosService relevo = new PublicarEventosService(outbox, publicacion -> {
        if (caido) {
            throw new PublicacionFallidaException("RabbitMQ no disponible");
        }
        publicadas.add(publicacion);
    }, () -> T0, new PoliticaReintento());

    @Test
    void publicaEnOrdenYMarcaLosPublicados() {
        Reserva reserva = reserva();
        outbox.registrar(EventoReserva.confirmada(reserva, T0));
        outbox.registrar(EventoReserva.completada(reserva, T0));

        assertThat(relevo.publicar()).isEqualTo(2);

        assertThat(publicadas).extracting(PublicacionPendiente::tipo)
                .containsExactly("ReservaConfirmada", "ReservaCompletada");
        assertThat(outbox.pendientes()).allSatisfy(p -> assertThat(p.publicadoEn).isEqualTo(T0));
    }

    @Test
    void conRabbitMqCaidoProgramaElReintentoYDevuelveLosDemasALaCola() {
        Reserva reserva = reserva();
        outbox.registrar(EventoReserva.confirmada(reserva, T0));
        outbox.registrar(EventoReserva.cancelada(reserva, reserva.idTurista(), T0));
        caido = true;

        assertThat(relevo.publicar()).isZero();

        assertThat(outbox.pendientes().get(0).intentos).isEqualTo(1);
        assertThat(outbox.pendientes().get(0).proximoIntento).isEqualTo(T0.plusSeconds(2));
        assertThat(outbox.pendientes().get(1).intentos).isZero();
        assertThat(outbox.pendientes().get(1).proximoIntento).isEqualTo(T0);
    }

    @Test
    void laPurgaBorraLosPublicadosHaceMasDe30Dias() {
        relevo.purgar();

        assertThat(outbox.limitePurga).isEqualTo(T0.minus(Duration.ofDays(30)));
    }
}
