package co.edu.unimagdalena.samaria360.analytics.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * ms-analytics de punta a punta (ADR-011): los eventos llegan por RabbitMQ (con la topología real), se guardan en
 * analytics_db y se consultan por la API con un JWT. RF63 (campana), RF30 (correo en Mailpit) y RF13 (auditoría).
 */
@PruebaIntegracion
class AnaliticaIntegracionTest {

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

    @Value("${prueba.mailpit.api}")
    private String mailpit;

    private void publicar(String clave, String tipo, UUID idEvento, Map<String, Object> datos) throws Exception {
        String sobre = json.writeValueAsString(Map.of("idEvento", idEvento.toString(), "tipo", tipo, "version", 1,
                "origen", "ms-booking", "ocurridoEn", "2030-03-01T15:00:00Z", "clave", "x", "datos", datos));
        rabbit.send(EXCHANGE, clave, MessageBuilder.withBody(sobre.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).setMessageId(idEvento.toString()).build());
    }

    private static Map<String, Object> confirmada(UUID turista, UUID reserva) {
        return Map.ofEntries(Map.entry("idReserva", reserva.toString()), Map.entry("idTurista", turista.toString()),
                Map.entry("idPrestador", PRESTADOR.toString()), Map.entry("idServicio", "65f0c0ffee0000000000000a"),
                Map.entry("nombreServicio", "Tour a Playa Cristal"), Map.entry("categoria", "Tour"),
                Map.entry("fechaInicio", "2030-03-10T13:00:00Z"), Map.entry("fechaFin", "2030-03-10T18:00:00Z"),
                Map.entry("personas", 4), Map.entry("cantidad", 4));
    }

    private long notificacionesDe(UUID usuario) {
        return mongo.count(Query.query(Criteria.where("usuarioId").is(usuario.toString())), "notificaciones");
    }

    @Test
    void unaReservaConfirmadaLlegaALaCampanaDeAmbosYAlCorreoDelPrestador() throws Exception {
        UUID turista = UUID.randomUUID();
        UUID reserva = UUID.randomUUID();
        UUID idEvento = UUID.randomUUID();

        publicar("booking.reserva.confirmada", "ReservaConfirmada", idEvento, confirmada(turista, reserva));
        // El mismo evento otra vez (entrega al menos una vez): no se duplica (ADR-011, punto 5).
        publicar("booking.reserva.confirmada", "ReservaConfirmada", idEvento, confirmada(turista, reserva));

        await().atMost(Duration.ofSeconds(15)).until(() -> notificacionesDe(turista) == 1);
        Thread.sleep(500);
        assertThat(notificacionesDe(turista)).isEqualTo(1);

        String bearerTurista = TokensDePrueba.bearer(TokensDePrueba.usuario(turista, "Turista", 0));
        JsonNode campana = json.readTree(mvc.perform(get("/api/v1/analytics/notificaciones")
                .header(HttpHeaders.AUTHORIZATION, bearerTurista)).andReturn().getResponse().getContentAsString());
        assertThat(campana.get("noLeidas").asLong()).isEqualTo(1);
        assertThat(campana.at("/elementos/0/tipo").asText()).isEqualTo("RESERVA_CONFIRMADA");
        assertThat(campana.at("/elementos/0/datos/nombreServicio").asText()).isEqualTo("Tour a Playa Cristal");
        String id = campana.at("/elementos/0/id").asText();

        // Otro usuario no puede marcar la notificación ajena.
        String bearerOtro = TokensDePrueba.bearer(TokensDePrueba.usuario(UUID.randomUUID(), "Turista", 0));
        assertThat(mvc.perform(post("/api/v1/analytics/notificaciones/" + id + "/lectura")
                .header(HttpHeaders.AUTHORIZATION, bearerOtro)).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(post("/api/v1/analytics/notificaciones/" + id + "/lectura")
                .header(HttpHeaders.AUTHORIZATION, bearerTurista)).andReturn().getResponse().getStatus()).isEqualTo(204);

        // RF30: el correo al Prestador, con el contacto pedido a ms-auth (DEC-280).
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            JsonNode mensajes = json.readTree(RestClient.create(mailpit).get()
                    .uri(u -> u.path("/api/v1/search").queryParam("query", "to:" + AuthSimulado.CORREO_PRESTADOR)
                            .build()).retrieve().body(String.class));
            assertThat(mensajes.path("messages_count").asInt()).isPositive();
        });
    }

    @Test
    void unMensajeSinElSobreVaALaColaDeMensajesFallidos() {
        RabbitAdmin admin = new RabbitAdmin(rabbit);
        long antes = admin.getQueueInfo("ms-analytics.eventos.v1.dlq").getMessageCount();

        rabbit.send(EXCHANGE, "booking.reserva.confirmada", MessageBuilder.withBody("no es json".getBytes()).build());

        await().atMost(Duration.ofSeconds(15)).until(() ->
                admin.getQueueInfo("ms-analytics.eventos.v1.dlq").getMessageCount() == antes + 1);
    }

    @Test
    void elAdministradorVeLaAuditoriaYOtroRolNo() throws Exception {
        UUID admin = UUID.randomUUID();
        UUID prestador = UUID.randomUUID();
        publicar("auth.cuenta.actualizada", "CuentaActualizada", UUID.randomUUID(), Map.of("idUsuario",
                admin.toString(), "rol", "Administrador", "estadoCuenta", "Activo", "version", 2));
        publicar("auth.prestador.validado", "PrestadorValidado", UUID.randomUUID(), Map.of("idPrestador",
                prestador.toString(), "decision", "Aprobado", "idAdministrador", admin.toString()));

        await().atMost(Duration.ofSeconds(15)).until(() -> mongo.count(Query.query(
                Criteria.where("usuarioId").is(admin.toString())), "logs_auditoria") == 1);

        // DEC-110: con la proyección, ver debe alcanzar la versión; un token viejo es 401.
        assertThat(mvc.perform(get("/api/v1/analytics/admin/auditoria").header(HttpHeaders.AUTHORIZATION,
                TokensDePrueba.bearer(TokensDePrueba.usuario(admin, "Administrador", 1)))).andReturn().getResponse()
                .getStatus()).isEqualTo(401);
        JsonNode pagina = json.readTree(mvc.perform(get("/api/v1/analytics/admin/auditoria")
                .param("accion", "PRESTADOR_APROBADO").param("usuario", admin.toString())
                .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.bearer(TokensDePrueba.usuario(admin,
                        "Administrador", 2)))).andReturn().getResponse().getContentAsString());
        assertThat(pagina.get("totalElementos").asLong()).isEqualTo(1);
        assertThat(pagina.at("/elementos/0/detalle").asText()).contains(prestador.toString());
        // DEC-322 (A005): los datos del detalle, para traducirlo en la interfaz; el validador v2 los acepta.
        assertThat(pagina.at("/elementos/0/datos/idPrestador").asText()).isEqualTo(prestador.toString());

        assertThat(mvc.perform(get("/api/v1/analytics/admin/auditoria").header(HttpHeaders.AUTHORIZATION,
                TokensDePrueba.bearer(TokensDePrueba.usuario(UUID.randomUUID(), "Turista", 0)))).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/api/v1/analytics/admin/auditoria").param("desde", "ayer")
                .header(HttpHeaders.AUTHORIZATION, TokensDePrueba.bearer(TokensDePrueba.usuario(admin,
                        "Administrador", 2)))).andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void sinTokenNoHayNotificaciones() throws Exception {
        assertThat(mvc.perform(get("/api/v1/analytics/notificaciones")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
    }
}
