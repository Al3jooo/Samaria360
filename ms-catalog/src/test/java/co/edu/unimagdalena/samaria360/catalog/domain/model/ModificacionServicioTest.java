package co.edu.unimagdalena.samaria360.catalog.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF41 (DEC-311): las modificaciones de precio que salen de RF32, con el valor anterior y el nuevo. */
class ModificacionServicioTest {

    private static final UUID PRESTADOR = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    private static final Instant AHORA = Instant.parse("2026-11-10T15:00:00Z");
    private static final String ID = "65a000000000000000000001";

    private static Supplier<String> idsFijos() {
        AtomicInteger n = new AtomicInteger();
        return () -> String.format("%024x", 0xB000 + n.incrementAndGet());
    }

    private static ServicioComercial alojamiento(String doble, String suite) {
        return RegistroServicio.registrar(Borradores.alojamiento(List.of(
                        Borradores.unidad("Doble", 4, 2, doble), Borradores.unidad("Suite", 1, 4, suite))),
                ID, idsFijos(), PRESTADOR, "RNT-1", true, Instant.parse("2026-10-24T15:00:00Z"));
    }

    @Test
    void enAlojamientoSoloLasUnidadesQueCambiaronConSuNombre() {
        List<ModificacionServicio> cambios = ModificacionServicio.dePrecio(alojamiento("150000", "320000"),
                alojamiento("150000.00", "300000"), AHORA);

        assertThat(cambios).singleElement().satisfies(m -> {
            assertThat(m.tipo()).isEqualTo(ModificacionServicio.Tipo.Precio);
            assertThat(m.detalle()).isEqualTo("Suite");
            assertThat(m.valorAnterior()).isEqualTo("320000");
            assertThat(m.valorNuevo()).isEqualTo("300000");
            assertThat(m.fecha()).isEqualTo(AHORA);
            assertThat(m.idServicio()).isEqualTo(ID);
            assertThat(m.prestadorId()).isEqualTo(PRESTADOR);
            assertThat(m.idEvento()).isNull();
        });
    }

    @Test
    void sinCambioDePrecioNoHayModificacion() {
        ServicioComercial tour = RegistroServicio.registrar(Borradores.tour(), ID, idsFijos(), PRESTADOR, "RNT-1",
                true, AHORA);

        assertThat(ModificacionServicio.dePrecio(tour, tour, AHORA)).isEmpty();
    }

    @Test
    void losCamposObligatoriosNoAdmitenNulos() {
        assertThatThrownBy(() -> new ModificacionServicio(ID, PRESTADOR, "x", ModificacionServicio.Tipo.Cupos, null,
                null, null, AHORA, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ModificacionServicio(ID, null, "x", ModificacionServicio.Tipo.Cupos, null,
                null, "5", AHORA, null)).isInstanceOf(NullPointerException.class);
    }
}
