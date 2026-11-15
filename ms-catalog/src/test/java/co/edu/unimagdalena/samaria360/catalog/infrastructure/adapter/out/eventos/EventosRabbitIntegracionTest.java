package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.out.eventos;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.PublicarEventosUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EstadoPublicacion;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EventoCatalogo;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.PruebaIntegracion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Outbox de catalog_db (M014) y relevo a RabbitMQ (ADR-011, DEC-293) contra MongoDB (réplica) y RabbitMQ reales: el
 * evento se escribe solo si la transacción se confirma, llega a la cola de ms-analytics y cumple eventos.schema.json.
 */
@PruebaIntegracion
class EventosRabbitIntegracionTest {

    @Autowired
    private OutboxEventosPort outbox;

    @Autowired
    private PublicarEventosUseCase relevo;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private MongoTransactionManager transacciones;

    @Autowired
    private ObjectMapper json;

    @Test
    void unServicioDecididoLlegaALaColaDeAnalyticsYCumpleElContrato() throws Exception {
        String idServicio = new ObjectId().toHexString();
        // Anterior a la hora fija de las pruebas (RelojFijo): el relevo lo encuentra vencido.
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> outbox.registrar(
                EventoCatalogo.servicioDecidido(idServicio, UUID.randomUUID(), EstadoPublicacion.Rechazado,
                        UUID.randomUUID(), Instant.parse("2026-01-01T15:00:00Z"))));

        while (relevo.publicar() > 0) {
            // drena el outbox
        }

        JsonNode recibido = null;
        Message mensaje;
        while ((mensaje = rabbit.receive("ms-analytics.eventos.v1", 2000)) != null) {
            JsonNode evento = json.readTree(new String(mensaje.getBody(), StandardCharsets.UTF_8));
            if (idServicio.equals(evento.get("clave").asText())) {
                recibido = evento;
            }
        }
        assertThat(recibido).as(String.valueOf(mongo.findOne(Query.query(Criteria.where("clave").is(idServicio)),
                org.bson.Document.class, "outbox_eventos"))).isNotNull();
        try (InputStream entrada = Files.newInputStream(Path.of("../docs/sad/api/eventos.schema.json"))) {
            JsonSchema esquema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(entrada);
            assertThat(esquema.validate(recibido)).isEmpty();
        }
        assertThat(recibido.get("origen").asText()).isEqualTo("ms-catalog");
        assertThat(recibido.at("/datos/decision").asText()).isEqualTo("Rechazado");
        assertThat(mongo.count(Query.query(Criteria.where("clave").is(idServicio).and("publicadoEn").exists(false)),
                "outbox_eventos")).isZero();
    }

    /** Si la transacción falla, el evento tampoco queda (outbox transaccional). */
    @Test
    void unaTransaccionRevertidaNoDejaElEvento() {
        String idServicio = new ObjectId().toHexString();

        assertThatThrownBy(() -> new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            outbox.registrar(EventoCatalogo.servicioDecidido(idServicio, UUID.randomUUID(),
                    EstadoPublicacion.Publicado, UUID.randomUUID(), Instant.now()));
            throw new IllegalStateException("falla después del evento");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(mongo.count(Query.query(Criteria.where("clave").is(idServicio)), "outbox_eventos")).isZero();
    }
}
