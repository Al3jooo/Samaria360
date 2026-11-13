package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.fake.OutboxEnMemoria;
import co.edu.unimagdalena.samaria360.auth.application.fake.RelojFijo;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Retención del outbox (DEC-272): el límite es «ahora» menos la retención, con el reloj del puerto. */
class PurgarOutboxServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");

    @Test
    void purgaLoEntregadoAntesDeAhoraMenosLaRetencion() {
        OutboxEnMemoria outbox = new OutboxEnMemoria();

        new PurgarOutboxService(outbox, new RelojFijo(AHORA), Duration.ofDays(30)).purgar();

        assertThat(outbox.limitePurga).isEqualTo(Instant.parse("2026-10-14T15:00:00Z"));
    }

    @Test
    void unaRetencionQueNoEsPositivaSeRechaza() {
        OutboxEnMemoria outbox = new OutboxEnMemoria();
        RelojFijo reloj = new RelojFijo(AHORA);

        assertThatThrownBy(() -> new PurgarOutboxService(outbox, reloj, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PurgarOutboxService(outbox, reloj, Duration.ofDays(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
