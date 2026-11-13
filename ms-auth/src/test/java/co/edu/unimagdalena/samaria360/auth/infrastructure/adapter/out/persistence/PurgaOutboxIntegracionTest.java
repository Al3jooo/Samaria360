package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.auth.application.port.out.OutboxPort;
import co.edu.unimagdalena.samaria360.auth.infrastructure.PruebaIntegracion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Purga del outbox contra PostgreSQL (DEC-272): solo se borran los eventos entregados a todos sus destinos antes del
 * límite, con sus entregas. Un evento con un destino pendiente o entregado hace poco se conserva. La base es compartida
 * con las demás pruebas de integración: cada prueba verifica solo los eventos que crea, sin vaciar el outbox.
 */
@PruebaIntegracion
class PurgaOutboxIntegracionTest {

    private static final UUID ID_TURISTA = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final Instant AHORA = Instant.parse("2026-11-13T15:00:00Z");
    private static final Instant LIMITE = AHORA.minus(Duration.ofDays(30));

    @Autowired
    private OutboxPort outbox;

    @Autowired
    private JdbcTemplate jdbc;

    /** Inserta un evento con una entrega por destino; {@code null} deja ese destino pendiente. */
    private long evento(Instant entregadoCatalogo, Instant entregadoReservas) {
        Long id = jdbc.queryForObject("""
                INSERT INTO outbox_evento (tipo, id_usuario, carga, creado_en)
                VALUES ('CuentaActualizada', ?, '{}'::jsonb, ?) RETURNING id_evento
                """, Long.class, ID_TURISTA, Timestamp.from(AHORA.minus(Duration.ofDays(60))));
        entrega(id, "ms-catalog", entregadoCatalogo);
        entrega(id, "ms-booking", entregadoReservas);
        return id;
    }

    private void entrega(long id, String destino, Instant entregado) {
        jdbc.update("INSERT INTO outbox_entrega (id_evento, destino, entregado_en, proximo_intento) VALUES (?, ?, ?, ?)",
                id, destino, entregado == null ? null : Timestamp.from(entregado), Timestamp.from(AHORA));
    }

    private boolean existe(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE id_evento = ?", Integer.class, id) == 1;
    }

    @Test
    void borraSoloLoEntregadoATodosLosDestinosAntesDelLimite() {
        long viejoEntregado = evento(AHORA.minus(Duration.ofDays(45)), AHORA.minus(Duration.ofDays(40)));
        long conDestinoPendiente = evento(AHORA.minus(Duration.ofDays(45)), null);
        long entregadoHacePoco = evento(AHORA.minus(Duration.ofDays(45)), AHORA.minus(Duration.ofDays(2)));

        int borrados = outbox.purgarEntregados(LIMITE);

        assertThat(borrados).isPositive();
        assertThat(existe(viejoEntregado)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_entrega WHERE id_evento = ?", Integer.class,
                viejoEntregado)).isZero();
        assertThat(existe(conDestinoPendiente)).isTrue();
        assertThat(existe(entregadoHacePoco)).isTrue();
    }

    @Test
    void unEventoSinEntregarNoSeBorraAunqueSeaViejo() {
        long pendiente = evento(null, null);

        outbox.purgarEntregados(LIMITE);

        assertThat(existe(pendiente)).isTrue();
    }
}
