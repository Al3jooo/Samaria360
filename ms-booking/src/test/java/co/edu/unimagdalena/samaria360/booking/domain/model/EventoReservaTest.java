package co.edu.unimagdalena.samaria360.booking.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Datos de los eventos de reservas (contrato de {@code eventos.md}; ADR-011, punto 7: sin datos personales). */
class EventoReservaTest {

    private static final Instant T0 = Instant.parse("2030-03-01T15:00:00Z");

    private static Reserva reserva() {
        return new Reserva(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "65f0c0ffee0000000000000a",
                "Suite Casa Tayrona", CategoriaReservable.Alojamiento, "65f0c0ffee0000000000000b", "Suite",
                EstadoReserva.Confirmada, 1, 2, new BigDecimal("300000"), UnidadCobro.values()[0],
                new BigDecimal("600000"), "COP", LocalDate.of(2030, 3, 10), LocalDate.of(2030, 3, 12), null,
                Instant.parse("2030-03-10T20:00:00Z"), Instant.parse("2030-03-12T17:00:00Z"), T0, null,
                UUID.randomUUID(), "hash");
    }

    @Test
    void laConfirmacionLlevaLoQueRf30PideMostrar() {
        Reserva r = reserva();

        assertThat(EventoReserva.confirmada(r, T0).datos())
                .containsEntry("nombreServicio", "Suite Casa Tayrona")
                .containsEntry("fechaInicio", "2030-03-10T20:00:00Z")
                .containsEntry("personas", 2)
                .containsEntry("categoria", "Alojamiento")
                .containsEntry("idUnidad", "65f0c0ffee0000000000000b")
                .containsEntry("cantidad", 1)
                .doesNotContainKeys("montoTotal", "precioUnitario", "claveIdempotencia", "hashSolicitud");
    }

    @Test
    void laCancelacionExigeQuienLaHizo() {
        assertThatThrownBy(() -> new EventoReserva(EventoReserva.Tipo.ReservaCancelada, reserva(), null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laCompletadaLlevaElFin() {
        assertThat(EventoReserva.completada(reserva(), T0).datos()).containsEntry("fechaFin", "2030-03-12T17:00:00Z")
                .doesNotContainKeys("personas", "canceladaPor");
    }
}
