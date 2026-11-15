package co.edu.unimagdalena.samaria360.booking.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.booking.application.port.out.OutboxModificacionesPort;
import co.edu.unimagdalena.samaria360.booking.domain.model.ModificacionOferta;
import co.edu.unimagdalena.samaria360.booking.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.PublicacionPendiente;
import co.edu.unimagdalena.samaria360.booking.domain.model.EventoReserva;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Outbox de {@code reservas_db} (V7; ADR-011, DEC-291). {@link #registrar} exige la transacción de la reserva
 * ({@code MANDATORY}): sin ella el evento y el cambio podrían separarse. El relevo usa transacciones cortas propias con
 * {@code FOR UPDATE SKIP LOCKED} y un arriendo, y publica fuera de ellas.
 */
@Component
public class OutboxEventosJdbcAdapter implements OutboxEventosPort, OutboxModificacionesPort {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OutboxEventosJdbcAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(EventoReserva evento) {
        String carga;
        try {
            carga = json.writeValueAsString(evento.datos());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento", e);
        }
        jdbc.update("""
                INSERT INTO outbox_evento (tipo, clave, carga, creado_en, proximo_intento)
                VALUES (?, ?, ?::jsonb, ?, ?)
                """, evento.tipo().name(), evento.reserva().idReserva().toString(), carga,
                Timestamp.from(evento.ocurridoEn()),
                Timestamp.from(evento.ocurridoEn()));
    }

    @Override
    @Transactional
    public List<PublicacionPendiente> reclamar(Instant ahora, Duration arriendo, int limite) {
        List<PublicacionPendiente> reclamadas = jdbc.query("""
                SELECT id_evento, id_publico, tipo, clave, creado_en, carga::text AS carga, intentos
                FROM outbox_evento
                WHERE publicado_en IS NULL AND proximo_intento <= ?
                ORDER BY id_evento
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, (rs, n) -> new PublicacionPendiente(rs.getLong("id_evento"),
                rs.getObject("id_publico", UUID.class), rs.getString("tipo"), rs.getString("clave"),
                rs.getTimestamp("creado_en").toInstant(), rs.getString("carga"), rs.getInt("intentos")),
                Timestamp.from(ahora), limite);
        Timestamp vence = Timestamp.from(ahora.plus(arriendo));
        for (PublicacionPendiente publicacion : reclamadas) {
            jdbc.update("UPDATE outbox_evento SET proximo_intento = ? WHERE id_evento = ?", vence,
                    publicacion.idEvento());
        }
        return reclamadas;
    }

    @Override
    @Transactional
    public void marcarPublicado(PublicacionPendiente publicacion, Instant ahora) {
        jdbc.update("UPDATE outbox_evento SET publicado_en = ? WHERE id_evento = ?", Timestamp.from(ahora),
                publicacion.idEvento());
    }

    @Override
    @Transactional
    public void registrarFallo(PublicacionPendiente publicacion, Instant proximoIntento, String error) {
        jdbc.update("""
                UPDATE outbox_evento SET intentos = intentos + 1, proximo_intento = ?, ultimo_error = ?
                WHERE id_evento = ?
                """, Timestamp.from(proximoIntento), error, publicacion.idEvento());
    }

    @Override
    @Transactional
    public void liberar(List<PublicacionPendiente> publicaciones, Instant ahora) {
        for (PublicacionPendiente publicacion : publicaciones) {
            jdbc.update("UPDATE outbox_evento SET proximo_intento = ? WHERE id_evento = ? AND publicado_en IS NULL",
                    Timestamp.from(ahora), publicacion.idEvento());
        }
    }

    @Override
    @Transactional
    public int purgarPublicados(Instant limite) {
        return jdbc.update("DELETE FROM outbox_evento WHERE publicado_en IS NOT NULL AND publicado_en < ?",
                Timestamp.from(limite));
    }

    /** RF19 (DEC-310): {@code OfertaModificada} con la clave del servicio, en la transacción del cambio. */
    @Override
    public void registrar(ModificacionOferta modificacion) {
        String carga;
        try {
            carga = json.writeValueAsString(modificacion.datos());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento", e);
        }
        jdbc.update("""
                INSERT INTO outbox_evento (tipo, clave, carga, creado_en, proximo_intento)
                VALUES ('OfertaModificada', ?, ?::jsonb, ?, ?)
                """, modificacion.idServicio(), carga, Timestamp.from(modificacion.ocurridoEn()),
                Timestamp.from(modificacion.ocurridoEn()));
    }
}
