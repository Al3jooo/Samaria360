package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultasFichasUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.PublicarEventosUseCase;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.PruebaIntegracion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEC-283 y DEC-307 contra MongoDB y RabbitMQ reales: las consultas al detalle que llegan por el gateway se cuentan, las
 * de la red interna no, y {@code ConsultasAgregadas} llega a la cola de ms-analytics con el contrato de eventos.md.
 */
@PruebaIntegracion
class ConsultasFichasIntegracionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ConsultasFichasUseCase consultas;

    @Autowired
    private PublicarEventosUseCase relevo;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private ObjectMapper json;

    @Test
    void lasConsultasPorElGatewayLleganComoConsultasAgregadas() throws Exception {
        JsonNode oferta = json.readTree(mvc.perform(get("/api/v1/catalogo/oferta").param("orden", "Nombre"))
                .andReturn().getResponse().getContentAsString());
        String servicio = null;
        String atractivo = null;
        for (JsonNode e : oferta.path("elementos")) {
            if (servicio == null && "Servicio".equals(e.path("tipo").asText())) {
                servicio = e.path("id").asText();
            }
            if (atractivo == null && "Atractivo".equals(e.path("tipo").asText())) {
                atractivo = e.path("id").asText();
            }
        }
        consultas.publicar();

        mvc.perform(get("/api/v1/catalogo/servicios/" + servicio).header("X-Forwarded-For", "203.0.113.5"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/catalogo/servicios/" + servicio).header("X-Forwarded-For", "203.0.113.6"))
                .andExpect(status().isOk());
        // ms-booking consulta el detalle por la red interna, sin X-Forwarded-For: no es una visita.
        mvc.perform(get("/api/v1/catalogo/servicios/" + servicio)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/catalogo/atractivos/" + atractivo).header("X-Forwarded-For", "203.0.113.5"))
                .andExpect(status().isOk());
        consultas.publicar();
        while (relevo.publicar() > 0) {
            // drena el outbox
        }

        List<JsonNode> recibidos = new ArrayList<>();
        Message mensaje;
        while ((mensaje = rabbit.receive("ms-analytics.eventos.v1", 2000)) != null) {
            JsonNode evento = json.readTree(new String(mensaje.getBody(), StandardCharsets.UTF_8));
            if ("ConsultasAgregadas".equals(evento.path("tipo").asText())) {
                recibidos.add(evento);
            }
        }
        JsonNode conteoServicio = null;
        JsonNode conteoAtractivo = null;
        JsonSchema esquema;
        try (InputStream entrada = Files.newInputStream(Path.of("../docs/sad/api/eventos.schema.json"))) {
            esquema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(entrada);
        }
        for (JsonNode evento : recibidos) {
            assertThat(esquema.validate(evento)).isEmpty();
            for (JsonNode conteo : evento.at("/datos/conteos")) {
                if (servicio.equals(conteo.path("idEntidad").asText())) {
                    conteoServicio = conteo;
                }
                if (atractivo.equals(conteo.path("idEntidad").asText())) {
                    conteoAtractivo = conteo;
                }
            }
        }
        assertThat(conteoServicio).as(recibidos.toString()).isNotNull();
        assertThat(conteoServicio.path("consultas").asLong()).isEqualTo(2);
        assertThat(conteoServicio.has("idPrestador")).isTrue();
        assertThat(conteoServicio.path("nombre").asText()).isNotBlank();
        assertThat(conteoAtractivo.path("consultas").asLong()).isEqualTo(1);
        assertThat(conteoAtractivo.has("idPrestador")).isFalse();
    }
}
