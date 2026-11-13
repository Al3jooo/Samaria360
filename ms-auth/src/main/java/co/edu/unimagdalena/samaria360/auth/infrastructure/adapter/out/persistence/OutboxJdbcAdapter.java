package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.auth.application.port.out.EntregaPendiente;
import co.edu.unimagdalena.samaria360.auth.application.port.out.OutboxPort;
import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoAdministrativo;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoCuentaActualizada;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outbox en {@code auth_db} (DEC-26). {@link #registrar} exige la transacción del cambio de estado
 * ({@code MANDATORY}): sin ella el evento y el cambio podrían separarse. El relevo usa transacciones cortas propias y
 * entrega fuera de ellas.
 */
@Component
public class OutboxJdbcAdapter implements OutboxPort {

    private static final String TIPO = "CuentaActualizada";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OutboxJdbcAdapter(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(EventoCuentaActualizada evento, List<Destino> destinos) {
        Map<String, Object> carga = new LinkedHashMap<>();
        carga.put("idUsuario", evento.idUsuario().toString());
        carga.put("rol", evento.rol().name());
        carga.put("estadoCuenta", evento.estadoCuenta().name());
        carga.put("version", evento.version());
        if (evento.rnt() != null) {
            carga.put("rnt", evento.rnt());
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO outbox_evento (tipo, id_usuario, carga, creado_en)
                VALUES (?, ?, ?::jsonb, ?) RETURNING id_evento
                """, Long.class, TIPO, evento.idUsuario(), escribir(carga), Timestamp.from(evento.ocurridoEn()));
        for (Destino destino : destinos) {
            jdbc.update("INSERT INTO outbox_entrega (id_evento, destino, proximo_intento) VALUES (?, ?, ?)",
                    id, destino.texto(), Timestamp.from(evento.ocurridoEn()));
        }
    }

    /**
     * La última entrega pendiente de cada usuario y destino que ya venció, bloqueada con SKIP LOCKED y con el arriendo
     * concedido en la misma transacción. Las anteriores del mismo usuario quedan reemplazadas por la última.
     */
    @Override
    @Transactional
    public List<EntregaPendiente> reclamar(Destino destino, Instant ahora, Duration arriendo, int limite) {
        List<EntregaPendiente> reclamadas = jdbc.query("""
                SELECT en.id_evento, en.intentos, e.id_usuario, e.carga::text AS carga
                FROM outbox_entrega en
                JOIN outbox_evento e ON e.id_evento = en.id_evento
                WHERE en.destino = ? AND en.entregado_en IS NULL AND en.proximo_intento <= ?
                  AND e.id_evento = (SELECT max(e2.id_evento)
                                     FROM outbox_evento e2
                                     JOIN outbox_entrega en2 ON en2.id_evento = e2.id_evento
                                     WHERE e2.id_usuario = e.id_usuario AND en2.destino = en.destino
                                       AND en2.entregado_en IS NULL)
                ORDER BY e.id_evento
                LIMIT ?
                FOR UPDATE OF en SKIP LOCKED
                """, (rs, n) -> {
            JsonNode carga = leer(rs.getString("carga"));
            // Los eventos anteriores a V17 no llevan rnt: la carga se lee tal como se guardó.
            JsonNode rnt = carga.get("rnt");
            return new EntregaPendiente(rs.getLong("id_evento"), rs.getObject("id_usuario", UUID.class),
                    Rol.valueOf(carga.get("rol").asText()),
                    EstadoAdministrativo.desdeTexto(carga.get("estadoCuenta").asText()),
                    carga.get("version").asLong(), rnt == null || rnt.isNull() ? null : rnt.asText(),
                    rs.getInt("intentos"));
        }, destino.texto(), Timestamp.from(ahora), limite);
        Timestamp vence = Timestamp.from(ahora.plus(arriendo));
        for (EntregaPendiente entrega : reclamadas) {
            jdbc.update("UPDATE outbox_entrega SET proximo_intento = ? WHERE id_evento = ? AND destino = ?",
                    vence, entrega.idEvento(), destino.texto());
        }
        return reclamadas;
    }

    @Override
    @Transactional
    public void marcarEntregada(Destino destino, EntregaPendiente entrega, Instant ahora) {
        jdbc.update("""
                UPDATE outbox_entrega en SET entregado_en = ?
                FROM outbox_evento e
                WHERE e.id_evento = en.id_evento AND en.destino = ? AND en.entregado_en IS NULL
                  AND e.id_usuario = ? AND en.id_evento <= ?
                """, Timestamp.from(ahora), destino.texto(), entrega.idUsuario(), entrega.idEvento());
    }

    @Override
    @Transactional
    public void registrarFallo(Destino destino, EntregaPendiente entrega, Instant proximoIntento, String error) {
        jdbc.update("""
                UPDATE outbox_entrega
                SET intentos = intentos + 1, proximo_intento = ?, ultimo_error = ?
                WHERE id_evento = ? AND destino = ?
                """, Timestamp.from(proximoIntento), error, entrega.idEvento(), destino.texto());
    }

    @Override
    @Transactional
    public void liberar(Destino destino, List<EntregaPendiente> entregas, Instant ahora) {
        for (EntregaPendiente entrega : entregas) {
            jdbc.update("UPDATE outbox_entrega SET proximo_intento = ? WHERE id_evento = ? AND destino = ?"
                    + " AND entregado_en IS NULL", Timestamp.from(ahora), entrega.idEvento(), destino.texto());
        }
    }

    /**
     * Borra, en una transacción, las entregas y los eventos que ya llegaron a todos sus destinos antes de {@code limite}
     * (DEC-272). La condición sobre {@code outbox_entrega} completa evita borrar un evento con algún destino pendiente.
     */
    @Override
    @Transactional
    public int purgarEntregados(Instant limite) {
        List<Long> eventos = jdbc.queryForList("""
                SELECT en.id_evento
                FROM outbox_entrega en
                GROUP BY en.id_evento
                HAVING bool_and(en.entregado_en IS NOT NULL) AND max(en.entregado_en) < ?
                """, Long.class, Timestamp.from(limite));
        if (eventos.isEmpty()) {
            return 0;
        }
        Long[] ids = eventos.toArray(Long[]::new);
        jdbc.update("DELETE FROM outbox_entrega WHERE id_evento = ANY (?)", (Object) ids);
        return jdbc.update("DELETE FROM outbox_evento WHERE id_evento = ANY (?)", (Object) ids);
    }

    private String escribir(Map<String, Object> carga) {
        try {
            return json.writeValueAsString(carga);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento", e);
        }
    }

    private JsonNode leer(String texto) {
        try {
            return json.readTree(texto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("La carga del evento no es JSON", e);
        }
    }
}
