package co.edu.unimagdalena.samaria360.analytics.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Calificaciones y reportes de punta a punta (DEC-300, DEC-301): la reserva Completada llega por RabbitMQ, el Turista
 * califica, el promedio sale como {@code PromedioActualizado} hacia la cola de ms-catalog, el Administrador modera con
 * su motivo en la auditoría y el Prestador ve lo recibido (RF56). RF57 con el panel del Administrador.
 */
@PruebaIntegracion
class CalificacionesIntegracionTest {

    private static final String EXCHANGE = "samaria360.eventos.v1";
    private static final UUID PRESTADOR = UUID.fromString(AuthSimulado.PRESTADOR);

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private void publicar(String clave, String tipo, Map<String, Object> datos) throws Exception {
        UUID idEvento = UUID.randomUUID();
        String sobre = json.writeValueAsString(Map.of("idEvento", idEvento.toString(), "tipo", tipo, "version", 1,
                "origen", tipo.startsWith("Reserva") ? "ms-booking" : "ms-auth", "ocurridoEn", "2030-03-01T15:00:00Z",
                "clave", "x", "datos", datos));
        rabbit.send(EXCHANGE, clave, MessageBuilder.withBody(sobre.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).setMessageId(idEvento.toString()).build());
    }

    private void reservaCompletada(UUID turista, String servicio) throws Exception {
        Map<String, Object> datos = Map.ofEntries(Map.entry("idReserva", UUID.randomUUID().toString()),
                Map.entry("idTurista", turista.toString()), Map.entry("idPrestador", PRESTADOR.toString()),
                Map.entry("idServicio", servicio), Map.entry("nombreServicio", "Tour a Playa Cristal"),
                Map.entry("categoria", "Tour"), Map.entry("fechaInicio", "2030-03-10T13:00:00Z"),
                Map.entry("fechaFin", "2030-03-10T18:00:00Z"), Map.entry("personas", 2), Map.entry("cantidad", 2));
        publicar("booking.reserva.confirmada", "ReservaConfirmada", datos);
        publicar("booking.reserva.completada", "ReservaCompletada", datos);
        await().atMost(Duration.ofSeconds(30)).until(() -> mongo.exists(Query.query(Criteria.where("idTurista")
                .is(turista.toString()).and("idServicio").is(servicio).and("estado").is("Completada")), "reservas"));
    }

    private void cuenta(UUID id, String rol, long version) throws Exception {
        publicar("auth.cuenta.actualizada", "CuentaActualizada", Map.of("idUsuario", id.toString(), "rol", rol,
                "estadoCuenta", "Activo", "version", version));
        await().atMost(Duration.ofSeconds(30)).until(() -> mongo.exists(Query.query(Criteria.where("_id")
                .is(id.toString()).and("version").is(version)), "cuentas"));
    }

    private MvcResult hacer(MockHttpServletRequestBuilder peticion, UUID usuario, String rol, long ver, Object cuerpo)
            throws Exception {
        peticion.header(HttpHeaders.AUTHORIZATION, TokensDePrueba.bearer(TokensDePrueba.usuario(usuario, rol, ver)));
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        }
        return mvc.perform(peticion).andReturn();
    }

    private JsonNode cuerpo(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    /** El PromedioActualizado de la entidad que llega a la cola de ms-catalog, con el sobre de eventos.md. */
    private JsonNode promedioPublicado(String idEntidad, long version) throws Exception {
        long limite = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < limite) {
            Message mensaje = rabbit.receive("ms-catalog.eventos.v1", 1_000);
            if (mensaje == null) {
                continue;
            }
            JsonNode sobre = json.readTree(mensaje.getBody());
            if (idEntidad.equals(sobre.path("clave").asText()) && sobre.path("datos").path("version").asLong() == version) {
                return sobre;
            }
        }
        throw new AssertionError("No llegó PromedioActualizado " + version + " de " + idEntidad);
    }

    @Test
    void elTuristaCalificaElAdministradorModeraYElPrestadorLoVe() throws Exception {
        UUID turista = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        String servicio = "6a00000000000000000000" + String.format("%02x", (int) (Math.random() * 200) + 20);
        cuenta(admin, "Administrador", 1);

        // Sin la reserva Completada: 403 y la elegibilidad lo dice.
        assertThat(hacer(post("/api/v1/analytics/calificaciones"), turista, "Turista", 0,
                Map.of("tipoEntidad", "servicio", "idEntidad", servicio, "estrellas", 4)).getResponse().getStatus())
                .isEqualTo(403);
        reservaCompletada(turista, servicio);
        assertThat(cuerpo(hacer(get("/api/v1/analytics/calificaciones/elegibilidad").param("tipo", "servicio")
                .param("id", servicio), turista, "Turista", 0, null)).path("puedeCalificar").asBoolean()).isTrue();

        MvcResult creada = hacer(post("/api/v1/analytics/calificaciones"), turista, "Turista", 0,
                Map.of("tipoEntidad", "servicio", "idEntidad", servicio, "estrellas", 4, "comentario", "Muy bueno"));
        assertThat(creada.getResponse().getStatus()).isEqualTo(201);
        String id = cuerpo(creada).path("id").asText();
        assertThat(hacer(post("/api/v1/analytics/calificaciones"), turista, "Turista", 0,
                Map.of("tipoEntidad", "servicio", "idEntidad", servicio, "estrellas", 2)).getResponse().getStatus())
                .isEqualTo(409);

        JsonNode sobre = promedioPublicado(servicio, 1);
        assertThat(sobre.path("tipo").asText()).isEqualTo("PromedioActualizado");
        assertThat(sobre.path("origen").asText()).isEqualTo("ms-analytics");
        assertThat(sobre.path("datos").path("promedio").asDouble()).isEqualTo(4.0);
        assertThat(sobre.path("datos").path("cantidad").asInt()).isEqualTo(1);
        assertThat(Pattern.matches("^[0-9a-f-]{36}$", sobre.path("idEvento").asText())).isTrue();

        // Pública y sin token: sin datos del turista.
        JsonNode publica = json.readTree(mvc.perform(get("/api/v1/analytics/calificaciones").param("tipo", "servicio")
                .param("id", servicio)).andReturn().getResponse().getContentAsString());
        assertThat(publica.path("promedio").asDouble()).isEqualTo(4.0);
        assertThat(publica.path("elementos").get(0).has("turistaId")).isFalse();

        // RF56: el Prestador ve lo recibido con el nombre del servicio.
        JsonNode recibidas = cuerpo(hacer(get("/api/v1/analytics/prestador/calificaciones").param("servicio", servicio),
                PRESTADOR, "Prestador", 0, null));
        assertThat(recibidas.path("cantidad").asInt()).isEqualTo(1);
        assertThat(recibidas.path("elementos").get(0).path("nombreServicio").asText()).isEqualTo("Tour a Playa Cristal");

        // RF29: ocultar con motivo → promedio sin calificaciones, auditoría con el motivo.
        assertThat(hacer(post("/api/v1/analytics/admin/calificaciones/" + id + "/ocultamiento"), admin,
                "Administrador", 1, Map.of("motivo", "")).getResponse().getStatus()).isEqualTo(400);
        MvcResult oculta = hacer(post("/api/v1/analytics/admin/calificaciones/" + id + "/ocultamiento"), admin,
                "Administrador", 1, Map.of("motivo", "Lenguaje ofensivo"));
        assertThat(cuerpo(oculta).path("estado").asText()).isEqualTo("Oculta");
        assertThat(promedioPublicado(servicio, 2).path("datos").path("promedio").isNull()).isTrue();
        assertThat(mongo.exists(Query.query(Criteria.where("accion").is("CALIFICACION_OCULTADA")
                .and("usuarioId").is(admin.toString()).and("detalle").regex("Lenguaje ofensivo")), "logs_auditoria"))
                .isTrue();
        MvcResult otraVez = hacer(post("/api/v1/analytics/admin/calificaciones/" + id + "/ocultamiento"), admin,
                "Administrador", 1, Map.of("motivo", "x"));
        assertThat(otraVez.getResponse().getStatus()).isEqualTo(409);
        assertThat(cuerpo(otraVez).path("estadoActual").asText()).isEqualTo("Oculta");

        // Eliminar es definitivo; el Turista no modera.
        assertThat(hacer(post("/api/v1/analytics/admin/calificaciones/" + id + "/eliminacion"), turista, "Turista", 0,
                Map.of("motivo", "x")).getResponse().getStatus()).isEqualTo(403);
        assertThat(hacer(post("/api/v1/analytics/admin/calificaciones/" + id + "/eliminacion"), admin,
                "Administrador", 1, Map.of("motivo", "Spam")).getResponse().getStatus()).isEqualTo(204);
        assertThat(mongo.exists(Query.query(Criteria.where("idEntidad").is(servicio)), "calificaciones")).isFalse();
    }

    @Test
    void elTuristaReportaYElAdministradorLoMarcaRevisado() throws Exception {
        UUID turista = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        cuenta(admin, "Administrador", 1);
        Map<String, Object> reporte = Map.of("tipoEntidad", "atractivo", "idEntidad", "6a0000000000000000000099",
                "motivo", "INFORMACION_DESACTUALIZADA", "descripcion", "El horario cambió");

        MvcResult creado = hacer(post("/api/v1/analytics/reportes"), turista, "Turista", 0, reporte);
        assertThat(creado.getResponse().getStatus()).isEqualTo(201);
        assertThat(hacer(post("/api/v1/analytics/reportes"), turista, "Turista", 0, reporte).getResponse().getStatus())
                .isEqualTo(409);
        assertThat(hacer(post("/api/v1/analytics/reportes"), turista, "Turista", 0,
                Map.of("tipoEntidad", "atractivo", "idEntidad", "6a0000000000000000000099", "motivo", "SPAM"))
                .getResponse().getStatus()).isEqualTo(400);

        String id = cuerpo(creado).path("id").asText();
        JsonNode pendientes = cuerpo(hacer(get("/api/v1/analytics/admin/reportes").param("estado", "Pendiente"), admin,
                "Administrador", 1, null));
        assertThat(pendientes.path("elementos").findValuesAsText("id")).contains(id);
        JsonNode revisado = cuerpo(hacer(post("/api/v1/analytics/admin/reportes/" + id + "/revision"), admin,
                "Administrador", 1, null));
        assertThat(revisado.path("estado").asText()).isEqualTo("Revisado");
        assertThat(hacer(post("/api/v1/analytics/admin/reportes/" + id + "/revision"), admin, "Administrador", 1, null)
                .getResponse().getStatus()).isEqualTo(409);
    }
}
