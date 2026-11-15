package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.eventos;

import co.edu.unimagdalena.samaria360.auth.application.port.in.PublicarEventosUseCase;
import co.edu.unimagdalena.samaria360.auth.infrastructure.PruebaIntegracion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Relevo del outbox hacia RabbitMQ (ADR-011) contra PostgreSQL y RabbitMQ reales, con la topología de
 * {@code infra/rabbitmq/definiciones.json}: el evento llega a la cola de ms-analytics, cumple
 * {@code docs/sad/api/eventos.schema.json} y no lleva datos personales ni el RNT.
 */
@PruebaIntegracion
class EventosRabbitIntegracionTest {

    private static final String COLA = "ms-analytics.eventos.v1";
    private static JsonSchema esquema;

    @Autowired
    private PublicarEventosUseCase relevo;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @BeforeAll
    static void cargarEsquema() throws Exception {
        try (InputStream entrada = Files.newInputStream(Path.of("../docs/sad/api/eventos.schema.json"))) {
            esquema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(entrada);
        }
    }

    @Test
    void unTuristaRegistradoSePublicaConSuPaisYCumpleElContrato() throws Exception {
        String correo = "evento-" + UUID.randomUUID() + "@samaria360.test";
        mvc.perform(post("/api/v1/auth/registro/turista").contentType(MediaType.APPLICATION_JSON).content("""
                {"primerNombre":"Ana","primerApellido":"Rojas","correo":"%s","contrasena":"Clave1234",
                 "paisOrigen":"PE","categoriasInteres":["Cultura"],"aceptoPoliticas":true}
                """.formatted(correo))).andReturn();
        UUID id = jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE correo = ?", UUID.class, correo);

        JsonNode evento = publicarYBuscar(id.toString());

        assertThat(validar(evento)).isEmpty();
        assertThat(evento.get("tipo").asText()).isEqualTo("CuentaActualizada");
        assertThat(evento.get("origen").asText()).isEqualTo("ms-auth");
        assertThat(evento.at("/datos/rol").asText()).isEqualTo("Turista");
        assertThat(evento.at("/datos/version").asLong()).isZero();
        assertThat(evento.at("/datos/pais").asText()).isEqualTo("PE");
        assertThat(evento.toString()).doesNotContain(correo).doesNotContain("Rojas");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_entrega en JOIN outbox_evento e ON e.id_evento = en.id_evento
                WHERE e.id_usuario = ? AND en.destino = 'rabbitmq' AND en.entregado_en IS NULL
                """, Integer.class, id)).isZero();
    }

    /** ADR-011, punto 7: el RNT solo viaja por HTTP a ms-catalog; en RabbitMQ, el estado de la cuenta sin él. */
    @Test
    void unPrestadorSePublicaSinRnt() throws Exception {
        String correo = "evento-" + UUID.randomUUID() + "@samaria360.test";
        String rnt = "RNT-" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/v1/auth/registro/prestador").contentType(MediaType.APPLICATION_JSON).content("""
                {"nombreRepresentanteLegal":"Luis Pérez","rnt":"%s","correoCorporativo":"%s","telefono":"3001234567",
                 "contrasena":"Clave1234","aceptoPoliticas":true}
                """.formatted(rnt, correo))).andReturn();
        UUID id = jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE correo = ?", UUID.class, correo);

        JsonNode evento = publicarYBuscar(id.toString());

        assertThat(validar(evento)).isEmpty();
        assertThat(evento.at("/datos/estadoCuenta").asText()).isEqualTo("Pendiente");
        assertThat(evento.get("datos").has("rnt")).isFalse();
        assertThat(evento.toString()).doesNotContain(rnt).doesNotContain("3001234567");
    }

    /** V19: cada cuenta que ya existía tiene su CuentaActualizada para RabbitMQ, sin subir la versión. */
    @Test
    void lasCuentasDeLaSemillaTienenSuEventoParaRabbitMq() {
        Integer sinEvento = jdbc.queryForObject("""
                SELECT count(*) FROM usuario u
                WHERE u.correo IN ('turista@samaria360.test', 'prestador@samaria360.test', 'gestor@samaria360.test',
                                   'admin@samaria360.test', 'prestador2@samaria360.test')
                  AND NOT EXISTS (SELECT 1 FROM outbox_evento e JOIN outbox_entrega en ON en.id_evento = e.id_evento
                                  WHERE e.id_usuario = u.id_usuario AND en.destino = 'rabbitmq')
                """, Integer.class);
        assertThat(sinEvento).isZero();
    }

    private JsonNode publicarYBuscar(String clave) throws Exception {
        while (relevo.publicar() > 0) {
            // drena el outbox: otras pruebas dejan eventos pendientes
        }
        List<JsonNode> recibidos = new ArrayList<>();
        Message mensaje;
        while ((mensaje = rabbit.receive(COLA, 2000)) != null) {
            recibidos.add(json.readTree(new String(mensaje.getBody(), StandardCharsets.UTF_8)));
            assertThat(mensaje.getMessageProperties().getMessageId())
                    .isEqualTo(recibidos.get(recibidos.size() - 1).get("idEvento").asText());
        }
        return recibidos.stream().filter(e -> clave.equals(e.get("clave").asText())).reduce((a, b) -> b)
                .orElseThrow(() -> new AssertionError("No llegó el evento de " + clave));
    }

    private static Set<ValidationMessage> validar(JsonNode evento) {
        return esquema.validate(evento);
    }
}
