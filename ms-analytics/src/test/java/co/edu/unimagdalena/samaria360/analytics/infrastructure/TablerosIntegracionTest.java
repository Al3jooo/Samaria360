package co.edu.unimagdalena.samaria360.analytics.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Tableros de punta a punta (DEC-308) con los eventos reales en RabbitMQ: RF14 (reservas por día, top 3 de atractivos
 * y turistas por país; Administrador y Gestor), RF31 (el Prestador) y RF64 (el Gestor), con el control de roles.
 */
@PruebaIntegracion
class TablerosIntegracionTest {

    /** H23 (DEC-323): un nombre comercial de más de 120 caracteres (ms-catalog admite 150) debe llegar a los tableros. */
    private static final String NOMBRE_LARGO = "Tour del panel " + "x".repeat(125);


    private static final String EXCHANGE = "samaria360.eventos.v1";

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private UUID publicar(String clave, String tipo, String origen, Map<String, Object> datos) throws Exception {
        UUID idEvento = UUID.randomUUID();
        String sobre = json.writeValueAsString(Map.of("idEvento", idEvento.toString(), "tipo", tipo, "version", 1,
                "origen", origen, "ocurridoEn", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), "clave", "x",
                "datos", datos));
        rabbit.send(EXCHANGE, clave, MessageBuilder.withBody(sobre.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).setMessageId(idEvento.toString()).build());
        return idEvento;
    }

    private void esperarProcesado(UUID idEvento) {
        await().atMost(Duration.ofSeconds(30)).until(() -> mongo.exists(Query.query(Criteria.where("_id")
                .is(idEvento.toString())), "eventos_procesados"));
    }

    private void cuenta(UUID id, String rol, String pais) throws Exception {
        Map<String, Object> datos = pais == null
                ? Map.of("idUsuario", id.toString(), "rol", rol, "estadoCuenta", "Activo", "version", 1)
                : Map.of("idUsuario", id.toString(), "rol", rol, "estadoCuenta", "Activo", "version", 1, "pais", pais);
        esperarProcesado(publicar("auth.cuenta.actualizada", "CuentaActualizada", "ms-auth", datos));
    }

    private JsonNode pedir(String ruta, UUID usuario, String rol, int estado) throws Exception {
        MvcResult r = mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION,
                TokensDePrueba.bearer(TokensDePrueba.usuario(usuario, rol, 1)))).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(estado);
        return estado == 200 ? json.readTree(r.getResponse().getContentAsString()) : null;
    }

    @Test
    void losTablerosSeArmanConLosEventosYRespetanLosRoles() throws Exception {
        UUID admin = UUID.randomUUID();
        UUID gestor = UUID.randomUUID();
        UUID prestador = UUID.randomUUID();
        cuenta(admin, "Administrador", null);
        cuenta(gestor, "Gestor", null);
        cuenta(UUID.randomUUID(), "Turista", "PE");
        cuenta(UUID.randomUUID(), "Turista", "PE");
        String servicio = "6b00000000000000000000" + String.format("%02x", (int) (Math.random() * 200) + 20);
        String atractivo = "6c00000000000000000000" + String.format("%02x", (int) (Math.random() * 200) + 20);

        esperarProcesado(publicar("booking.reserva.confirmada", "ReservaConfirmada", "ms-booking", Map.ofEntries(
                Map.entry("idReserva", UUID.randomUUID().toString()), Map.entry("idTurista", UUID.randomUUID().toString()),
                Map.entry("idPrestador", prestador.toString()), Map.entry("idServicio", servicio),
                Map.entry("nombreServicio", NOMBRE_LARGO), Map.entry("categoria", "Tour"),
                Map.entry("fechaInicio", "2030-03-10T13:00:00Z"), Map.entry("fechaFin", "2030-03-10T18:00:00Z"),
                Map.entry("personas", 2), Map.entry("cantidad", 2))));
        String ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        esperarProcesado(publicar("catalog.ficha.consultas-agregadas", "ConsultasAgregadas", "ms-catalog", Map.of(
                "ventanaInicio", ahora, "ventanaFin", ahora,
                "conteos", List.of(
                        Map.of("tipoEntidad", "servicio", "idEntidad", servicio, "nombre", NOMBRE_LARGO,
                                "idPrestador", prestador.toString(), "consultas", 7),
                        Map.of("tipoEntidad", "atractivo", "idEntidad", atractivo, "nombre", "Atractivo top",
                                "consultas", 100000)))));
        esperarProcesado(publicar("catalog.atractivo.ocupacion-registrada", "OcupacionRegistrada", "ms-catalog",
                Map.of("idAtractivo", atractivo, "nombreAtractivo", "Atractivo top", "categoria", "Naturaleza",
                        "ocupacion", 90, "limiteCapacidadCarga", 100, "porcentaje", 90, "nivel", "Alto")));

        // RF14: el Administrador y el Gestor; el Prestador no.
        JsonNode general = pedir("/api/v1/analytics/tableros/general", admin, "Administrador", 200);
        assertThat(general.path("totalReservas").asLong()).isPositive();
        assertThat(general.path("reservasPorDia")).hasSize(30);
        assertThat(general.path("atractivosMasConsultados").get(0).path("nombre").asText()).isEqualTo("Atractivo top");
        assertThat(general.path("visitantesPorZona").findValuesAsText("pais")).contains("PE");
        pedir("/api/v1/analytics/tableros/general", gestor, "Gestor", 200);
        pedir("/api/v1/analytics/tableros/general", prestador, "Prestador", 403);
        pedir("/api/v1/analytics/tableros/general?desde=ayer", admin, "Administrador", 400);

        // RF31: el Prestador ve consultas y reservas de su servicio.
        JsonNode panel = pedir("/api/v1/analytics/prestador/panel?dias=7", prestador, "Prestador", 200);
        JsonNode fila = null;
        for (JsonNode s : panel.path("servicios")) {
            if (servicio.equals(s.path("idServicio").asText())) {
                fila = s;
            }
        }
        assertThat(fila).as(panel.toString()).isNotNull();
        assertThat(fila.path("consultas").asLong()).isEqualTo(7);
        assertThat(fila.path("reservas").asLong()).isEqualTo(1);
        assertThat(fila.path("nombre").asText()).isEqualTo(NOMBRE_LARGO);
        pedir("/api/v1/analytics/prestador/panel?dias=15", prestador, "Prestador", 400);

        // RF64: el Gestor filtra por atractivo; el Administrador no lo ve.
        JsonNode afluencia = pedir("/api/v1/analytics/gestor/afluencia?atractivo=" + atractivo, gestor, "Gestor", 200);
        assertThat(afluencia.path("atractivos").get(0).path("porcentajeMaximo").asInt()).isEqualTo(90);
        assertThat(afluencia.path("atractivos").get(0).path("ultimoNivel").asText()).isEqualTo("Alto");
        assertThat(afluencia.path("porDia")).hasSize(1);
        assertThat(afluencia.path("atractivosDisponibles").findValuesAsText("idAtractivo")).contains(atractivo);
        pedir("/api/v1/analytics/gestor/afluencia", admin, "Administrador", 403);
    }
}
