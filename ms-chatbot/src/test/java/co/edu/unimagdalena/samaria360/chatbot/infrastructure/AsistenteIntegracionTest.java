package co.edu.unimagdalena.samaria360.chatbot.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ms-chatbot de punta a punta (DEC-297, DEC-298): RF04 y RF36 con la búsqueda pública simulada, RF59 en los dos
 * idiomas, RF35 contra chatbot_db (C001), RF05 desde el Administrador y el validador de {@code ver} con la proyección
 * que llega por RabbitMQ.
 */
@PruebaIntegracion
class AsistenteIntegracionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private RabbitTemplate rabbit;

    @BeforeEach
    void limpiar() {
        CatalogoSimulado.caido = false;
        CatalogoSimulado.CONSULTAS.clear();
    }

    private static String turista(UUID id) {
        return TokensDePrueba.bearer(TokensDePrueba.usuario(id, "Turista", 0));
    }

    private JsonNode enviar(UUID turista, String cuerpo, int estado) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/chatbot/mensajes").header(HttpHeaders.AUTHORIZATION, turista(turista))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(estado);
        return json.readTree(r.getResponse().getContentAsByteArray());
    }

    private void proyectar(UUID id, String rol, long version) throws Exception {
        String sobre = json.writeValueAsString(Map.of("idEvento", UUID.randomUUID().toString(),
                "tipo", "CuentaActualizada", "version", 1, "origen", "ms-auth", "ocurridoEn", "2030-03-01T15:00:00Z",
                "clave", id.toString(), "datos", Map.of("idUsuario", id.toString(), "rol", rol,
                        "estadoCuenta", "Activo", "version", version)));
        rabbit.send("samaria360.eventos.v1", "auth.cuenta.actualizada",
                MessageBuilder.withBody(sobre.getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON).build());
    }

    @Test
    void unItinerarioSaleDelCatalogoConSuJustificacionYSinToken() throws Exception {
        JsonNode r = enviar(UUID.randomUUID(),
                "{\"mensaje\":\"Arma un itinerario de 2 días con tours\",\"idioma\":\"es\",\"intereses\":[\"Aventura\"]}",
                200);

        assertThat(r.path("sugerencias").get(0).path("id").asText()).isEqualTo(CatalogoSimulado.TOUR);
        assertThat(r.path("sugerencias").get(0).path("criterio").asText()).isEqualTo("PREFERENCIAS");
        assertThat(r.path("itinerario").size()).isPositive();
        assertThat(r.path("respuesta").asText()).contains("itinerario de");
        assertThat(r.path("mensajesRestantes").asInt()).isEqualTo(19);
        assertThat(CatalogoSimulado.CONSULTAS).isNotEmpty()
                .allMatch(c -> c.contains("categoria=Tour") && !c.endsWith("|token"));
    }

    /** RF59 y RF33: en inglés y sin el atractivo cerrado. */
    @Test
    void respondeEnElIdiomaDeLaInterfazSinSugerirLoCerrado() throws Exception {
        JsonNode r = enviar(UUID.randomUUID(), "{\"mensaje\":\"beaches please\",\"idioma\":\"en\"}", 200);

        assertThat(r.path("respuesta").asText()).startsWith("I found these options");
        assertThat(r.path("sugerencias").size()).isEqualTo(1);
        assertThat(r.path("sugerencias").get(0).path("id").asText()).isEqualTo(CatalogoSimulado.PLAYA);
        assertThat(r.path("sugerencias").get(0).path("justificacion").asText())
                .isEqualTo("Open now and available to visit.");
    }

    @Test
    void conElCatalogoCaidoRespondeYLoDice() throws Exception {
        CatalogoSimulado.caido = true;

        JsonNode r = enviar(UUID.randomUUID(), "{\"mensaje\":\"un hotel\",\"idioma\":\"es\"}", 200);

        assertThat(r.path("catalogoDisponible").asBoolean()).isFalse();
        assertThat(r.path("respuesta").asText()).startsWith("No pude consultar el catálogo");
    }

    /** Decisión 5 del equipo: 20 mensajes por turista al día; el 21 es 429 y no llega al catálogo. */
    @Test
    void elMensajeVeintiunoEs429() throws Exception {
        UUID id = UUID.randomUUID();
        for (int i = 0; i < 20; i++) {
            enviar(id, "{\"mensaje\":\"hola\"}", 200);
        }

        JsonNode r = enviar(id, "{\"mensaje\":\"un hotel\"}", 429);

        assertThat(r.path("codigo").asText()).isEqualTo("LIMITE_MENSAJES");
        assertThat(CatalogoSimulado.CONSULTAS).isEmpty();
        assertThat(mongo.count(Query.query(Criteria.where("turistaId").is(id.toString())), "uso_diario"))
                .isEqualTo(1);
    }

    @Test
    void soloElTuristaConversaYUnMensajeLargoEs400() throws Exception {
        mvc.perform(post("/api/v1/chatbot/mensajes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/chatbot/mensajes").header(HttpHeaders.AUTHORIZATION,
                                TokensDePrueba.bearer(TokensDePrueba.usuario(UUID.randomUUID(), "Prestador", 0)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\":\"x\"}"))
                .andExpect(status().isForbidden());
        JsonNode r = enviar(UUID.randomUUID(), "{\"mensaje\":\"" + "x".repeat(501) + "\"}", 400);
        assertThat(r.path("campos").path("mensaje").asText()).isEqualTo("LONGITUD_MAXIMA");
    }

    /** RF35: guardar, listar, editar y borrar; un itinerario ajeno es 404. */
    @Test
    void elTuristaGuardaEditaYBorraSusItinerarios() throws Exception {
        UUID duena = UUID.randomUUID();
        String cuerpo = "{\"nombre\":\"Fin de semana\",\"items\":["
                + "{\"orden\":1,\"tipo\":\"atractivo\",\"refId\":\"" + CatalogoSimulado.PLAYA
                + "\",\"tituloActividad\":\"Playa Cristal\",\"diaSugerido\":1},"
                + "{\"orden\":2,\"tipo\":\"servicio\",\"refId\":\"" + CatalogoSimulado.TOUR
                + "\",\"tituloActividad\":\"Tour Tayrona\",\"diaSugerido\":2}]}";
        MvcResult creado = mvc.perform(post("/api/v1/chatbot/itinerarios")
                        .header(HttpHeaders.AUTHORIZATION, turista(duena))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated()).andReturn();
        String id = json.readTree(creado.getResponse().getContentAsByteArray()).path("id").asText();

        mvc.perform(get("/api/v1/chatbot/itinerarios").header(HttpHeaders.AUTHORIZATION, turista(duena)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.elementos.length()").value(1))
                .andExpect(jsonPath("$.elementos[0].items[1].diaSugerido").value(2));
        mvc.perform(get("/api/v1/chatbot/itinerarios/" + id)
                        .header(HttpHeaders.AUTHORIZATION, turista(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/chatbot/itinerarios/" + id).header(HttpHeaders.AUTHORIZATION, turista(duena))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo.replace("Fin de semana", "Puente")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Puente"));
        mvc.perform(post("/api/v1/chatbot/itinerarios").header(HttpHeaders.AUTHORIZATION, turista(duena))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"\",\"items\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.nombre").value("REQUERIDO"));
        mvc.perform(delete("/api/v1/chatbot/itinerarios/" + id).header(HttpHeaders.AUTHORIZATION, turista(duena)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/chatbot/itinerarios/" + id).header(HttpHeaders.AUTHORIZATION, turista(duena)))
                .andExpect(status().isNotFound());
    }

    /** RF05: el Administrador alimenta la base y el asistente la usa en la siguiente respuesta. */
    @Test
    void elAdministradorAlimentaLaBaseYElAsistenteLaUsa() throws Exception {
        // DEC-110: un Administrador sin fila en la proyección es SESION_INVALIDADA; llega por RabbitMQ.
        UUID idAdmin = UUID.randomUUID();
        proyectar(idAdmin, "Administrador", 1);
        String admin = TokensDePrueba.bearer(TokensDePrueba.usuario(idAdmin, "Administrador", 1));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mvc.perform(get("/api/v1/chatbot/admin/conocimiento").header(HttpHeaders.AUTHORIZATION, admin))
                        .andExpect(status().isOk()));
        MvcResult creada = mvc.perform(post("/api/v1/chatbot/admin/conocimiento")
                        .header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contenido\":\"En la Ciénaga Grande se avistan aves al amanecer.\","
                                + "\"categoria\":\"Aves\"}"))
                .andExpect(status().isCreated()).andReturn();
        String id = json.readTree(creada.getResponse().getContentAsByteArray()).path("id").asText();

        JsonNode r = enviar(UUID.randomUUID(), "{\"mensaje\":\"¿dónde ver aves?\"}", 200);

        assertThat(r.path("respuesta").asText()).contains("Ciénaga Grande");
        assertThat(r.path("fuentes").toString()).isEqualTo("[\"Aves\"]");
        mvc.perform(get("/api/v1/chatbot/admin/conocimiento").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(get("/api/v1/chatbot/admin/conocimiento")
                        .header(HttpHeaders.AUTHORIZATION, turista(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/chatbot/admin/conocimiento/" + id).header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNoContent());
    }

    /** DEC-110 con la proyección que alimenta CuentaActualizada por RabbitMQ (ADR-011). */
    @Test
    void unTokenConVersionViejaEsSesionInvalidada() throws Exception {
        UUID id = UUID.randomUUID();
        proyectar(id, "Turista", 2);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                mvc.perform(get("/api/v1/chatbot/itinerarios").header(HttpHeaders.AUTHORIZATION,
                                TokensDePrueba.bearer(TokensDePrueba.usuario(id, "Turista", 1))))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.codigo").value("SESION_INVALIDADA")));
        mvc.perform(get("/api/v1/chatbot/itinerarios").header(HttpHeaders.AUTHORIZATION,
                        TokensDePrueba.bearer(TokensDePrueba.usuario(id, "Turista", 2))))
                .andExpect(status().isOk());
    }
}
