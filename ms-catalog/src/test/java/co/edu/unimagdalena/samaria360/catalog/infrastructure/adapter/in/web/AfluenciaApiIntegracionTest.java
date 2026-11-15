package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.catalog.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.PublicarEventosUseCase;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.PruebaIntegracion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoDatabase;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.SolicitudesAtractivo.tokenGestor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Afluencia de punta a punta (DEC-304, DEC-305) contra MongoDB (réplica) y RabbitMQ reales: el Gestor registra la
 * ocupación, la ficha pública ve el nivel, el límite alcanzado (RF08) y los alternativos (RF51), la búsqueda muestra el
 * nivel en la tarjeta (RF39) y {@code OcupacionRegistrada} llega a la cola de ms-analytics con el contrato.
 */
@PruebaIntegracion
class AfluenciaApiIntegracionTest {

    /** 11:00 en Santa Marta. */
    private static final Instant AHORA = Instant.parse("2026-10-30T16:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private RelojFijo reloj;

    @Autowired
    private PublicarEventosUseCase relevo;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private ObjectMapper json;

    private final UUID gestor = UUID.randomUUID();
    private final List<ObjectId> creados = new ArrayList<>();

    @BeforeEach
    void preparar() {
        reloj.ajustar(AHORA);
        SolicitudesAtractivo.filaDeCuenta(base(), gestor, "Gestor", 1);
    }

    @AfterEach
    void limpiar() {
        creados.forEach(id -> base().getCollection("atractivos").deleteOne(new Document("_id", id)));
        base().getCollection("ocupaciones").deleteMany(new Document("gestorId", gestor.toString()));
        SolicitudesAtractivo.limpiar(base(), gestor);
    }

    private MongoDatabase base() {
        return mongo.getDb();
    }

    /** Un atractivo de Cultura cerca del Centro Histórico, con o sin una lectura de hoy. */
    private String atractivo(String nombre, double lng, Integer ocupacion) {
        ObjectId id = new ObjectId();
        Document documento = new Document("_id", id)
                .append("gestorId", gestor.toString())
                .append("nombre", nombre)
                .append("coordenadas", new Document("type", "Point").append("coordinates", List.of(lng, 11.24)))
                .append("descripcion", "Para la prueba de afluencia")
                .append("categoria", "Cultura")
                .append("imagenes", List.of("/imagenes/catalogo/cascada-el-salto.webp"))
                .append("limiteCapacidadCarga", 100)
                .append("estadoOperativo", "Abierto")
                .append("fechaRegistro", Date.from(AHORA))
                .append("version", 0);
        if (ocupacion != null) {
            documento.append("ocupacionActual", new Document("valor", ocupacion).append("fecha", Date.from(AHORA)));
        }
        base().getCollection("atractivos").insertOne(documento);
        creados.add(id);
        return id.toHexString();
    }

    private JsonNode leer(String ruta) throws Exception {
        return json.readTree(mvc.perform(get(ruta)).andReturn().getResponse().getContentAsString());
    }

    @Test
    void elGestorRegistraLaOcupacionYLaFichaMuestraElNivelElLimiteYLosAlternativos() throws Exception {
        String museo = atractivo("Museo de prueba J", -74.211, null);
        String casa = atractivo("Casa de prueba J", -74.212, 30);
        String lejano = atractivo("Fuerte de prueba J", -74.300, 30);

        // Sin lectura de hoy: sin afluencia.
        assertThat(leer("/api/v1/catalogo/atractivos/" + museo + "/afluencia").path("afluencia").isNull()).isTrue();

        mvc.perform(post("/api/v1/catalogo/gestor/atractivos/" + museo + "/ocupacion")
                        .header(HttpHeaders.AUTHORIZATION, tokenGestor(gestor, 1))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ocupacion\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value("Alto"))
                .andExpect(jsonPath("$.porcentaje").value(100))
                .andExpect(jsonPath("$.limiteAlcanzado").value(true));

        JsonNode respuesta = leer("/api/v1/catalogo/atractivos/" + museo + "/afluencia");
        assertThat(respuesta.path("afluencia").path("nivel").asText()).isEqualTo("Alto");
        assertThat(respuesta.path("afluencia").path("limiteAlcanzado").asBoolean()).isTrue();
        List<String> alternativos = respuesta.path("alternativos").findValuesAsText("id");
        assertThat(alternativos).startsWith(casa, lejano);
        assertThat(respuesta.path("alternativos").get(0).path("nivel").asText()).isEqualTo("Bajo");

        assertThat(mongo.count(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("atractivoId").is(museo)), "ocupaciones"))
                .isEqualTo(1);

        // RF39 en la tarjeta de WF-05.
        JsonNode busqueda = json.readTree(mvc.perform(get("/api/v1/catalogo/oferta").param("texto", "Museo de prueba J"))
                .andReturn().getResponse().getContentAsString());
        assertThat(busqueda.path("elementos").get(0).path("afluencia").asText()).isEqualTo("Alto");

        // OcupacionRegistrada llega a ms-analytics con el contrato de eventos.md.
        while (relevo.publicar() > 0) {
            // drena el outbox
        }
        JsonNode recibido = null;
        Message mensaje;
        while ((mensaje = rabbit.receive("ms-analytics.eventos.v1", 2000)) != null) {
            JsonNode evento = json.readTree(new String(mensaje.getBody(), StandardCharsets.UTF_8));
            if (museo.equals(evento.path("clave").asText())) {
                recibido = evento;
            }
        }
        assertThat(recibido).isNotNull();
        try (InputStream entrada = Files.newInputStream(Path.of("../docs/sad/api/eventos.schema.json"))) {
            JsonSchema esquema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(entrada);
            assertThat(esquema.validate(recibido)).isEmpty();
        }
        assertThat(recibido.at("/datos/nivel").asText()).isEqualTo("Alto");
        assertThat(recibido.at("/datos/nombreAtractivo").asText()).isEqualTo("Museo de prueba J");
    }

    @Test
    void soloElGestorRegistraYLaOcupacionSeValida() throws Exception {
        String museo = atractivo("Museo de validación J", -74.211, null);

        mvc.perform(post("/api/v1/catalogo/gestor/atractivos/" + museo + "/ocupacion")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ocupacion\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/catalogo/gestor/atractivos/" + museo + "/ocupacion")
                        .header(HttpHeaders.AUTHORIZATION, tokenGestor(gestor, 1))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ocupacion\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.ocupacion").value("VALOR_MINIMO"));
        mvc.perform(post("/api/v1/catalogo/gestor/atractivos/" + new ObjectId().toHexString() + "/ocupacion")
                        .header(HttpHeaders.AUTHORIZATION, tokenGestor(gestor, 1))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ocupacion\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/catalogo/atractivos/no-es-un-id/afluencia")).andExpect(status().isNotFound());
    }
}
