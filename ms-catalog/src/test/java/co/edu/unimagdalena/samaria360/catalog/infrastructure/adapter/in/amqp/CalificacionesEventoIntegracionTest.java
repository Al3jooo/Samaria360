package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.amqp;

import co.edu.unimagdalena.samaria360.catalog.infrastructure.PruebaIntegracion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * {@code PromedioActualizado} de ms-analytics (DEC-302) contra RabbitMQ y MongoDB reales: el promedio se copia en el
 * servicio o el atractivo solo si la versión es mayor, la búsqueda lo expone y ordena por él (RF61), y un mensaje fuera
 * del contrato va a la {@code .dlq}.
 */
@PruebaIntegracion
class CalificacionesEventoIntegracionTest {

    private static final String EXCHANGE = "samaria360.eventos.v1";
    private static final String CLAVE = "analytics.calificacion.promedio-actualizado";

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private final List<String[]> tocados = new ArrayList<>();

    @AfterEach
    void limpiar() {
        tocados.forEach(t -> mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(t[1]))),
                new Update().unset("calificacion"), t[0]));
    }

    private void publicar(String tipoEntidad, String id, Double promedio, long cantidad, long version) throws Exception {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipoEntidad", tipoEntidad);
        datos.put("idEntidad", id);
        datos.put("promedio", promedio);
        datos.put("cantidad", cantidad);
        datos.put("version", version);
        enviar(json.writeValueAsString(Map.of("idEvento", UUID.randomUUID().toString(), "tipo", "PromedioActualizado",
                "version", 1, "origen", "ms-analytics", "ocurridoEn", "2030-03-01T15:00:00Z", "clave", id,
                "datos", datos)));
        tocados.add(new String[]{tipoEntidad.equals("servicio") ? "servicios" : "atractivos", id});
    }

    private void enviar(String cuerpo) {
        rabbit.send(EXCHANGE, CLAVE, MessageBuilder.withBody(cuerpo.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build());
    }

    private Document calificacion(String coleccion, String id) {
        Document d = mongo.findById(new ObjectId(id), Document.class, coleccion);
        return d == null ? null : d.get("calificacion", Document.class);
    }

    private JsonNode oferta(String consulta) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/catalogo/oferta?" + consulta)).andReturn().getResponse()
                .getContentAsString());
    }

    @Test
    void elPromedioLlegaSoloAvanzaConVersionMayorYOrdenaLaBusqueda() throws Exception {
        JsonNode todos = oferta("tamano=10&orden=Nombre");
        String servicio = null;
        String atractivo = null;
        for (JsonNode e : todos.path("elementos")) {
            if (servicio == null && "Servicio".equals(e.path("tipo").asText())) {
                servicio = e.path("id").asText();
            }
            if (atractivo == null && "Atractivo".equals(e.path("tipo").asText())) {
                atractivo = e.path("id").asText();
            }
        }
        assertThat(servicio).isNotNull();
        assertThat(atractivo).isNotNull();
        String idServicio = servicio;
        String idAtractivo = atractivo;

        publicar("servicio", idServicio, 4.5, 2, 3);
        publicar("atractivo", idAtractivo, 3.0, 1, 1);
        await().atMost(Duration.ofSeconds(15)).until(() -> calificacion("servicios", idServicio) != null
                && calificacion("atractivos", idAtractivo) != null);

        // Una versión vieja (entregada tarde) no cambia nada.
        publicar("servicio", idServicio, 1.0, 1, 2);
        publicar("servicio", idServicio, 5.0, 3, 4);
        await().atMost(Duration.ofSeconds(15)).until(() ->
                ((Number) calificacion("servicios", idServicio).get("version")).longValue() == 4);
        assertThat(calificacion("servicios", idServicio).getDouble("promedio")).isEqualTo(5.0);

        JsonNode porCalificacion = oferta("orden=Calificacion");
        JsonNode primero = porCalificacion.path("elementos").get(0);
        JsonNode segundo = porCalificacion.path("elementos").get(1);
        assertThat(primero.path("id").asText()).isEqualTo(idServicio);
        assertThat(primero.path("calificacion").asDouble()).isEqualTo(5.0);
        assertThat(primero.path("cantidadCalificaciones").asLong()).isEqualTo(3);
        assertThat(segundo.path("id").asText()).isEqualTo(idAtractivo);
        assertThat(porCalificacion.path("elementos").get(2).path("calificacion").isNull()).isTrue();
        assertThat(porCalificacion.path("elementos").get(2).path("cantidadCalificaciones").asLong()).isZero();

        // Sin calificaciones visibles: el promedio queda nulo y la entidad va al final.
        publicar("atractivo", idAtractivo, null, 0, 2);
        await().atMost(Duration.ofSeconds(15)).until(() ->
                ((Number) calificacion("atractivos", idAtractivo).get("version")).longValue() == 2);
        assertThat(calificacion("atractivos", idAtractivo).get("promedio")).isNull();
    }

    @Test
    void unPromedioFueraDelContratoVaALaColaDeMensajesFallidos() throws Exception {
        RabbitAdmin admin = new RabbitAdmin(rabbit);
        long antes = admin.getQueueInfo("ms-catalog.eventos.v1.dlq").getMessageCount();

        enviar(json.writeValueAsString(Map.of("idEvento", UUID.randomUUID().toString(), "tipo", "PromedioActualizado",
                "version", 1, "origen", "ms-analytics", "ocurridoEn", "2030-03-01T15:00:00Z", "clave", "x",
                "datos", Map.of("tipoEntidad", "servicio", "idEntidad", "no-es-un-id", "promedio", 9, "cantidad", 1,
                        "version", 1))));

        await().atMost(Duration.ofSeconds(15)).until(() ->
                admin.getQueueInfo("ms-catalog.eventos.v1.dlq").getMessageCount() == antes + 1);
    }
}
