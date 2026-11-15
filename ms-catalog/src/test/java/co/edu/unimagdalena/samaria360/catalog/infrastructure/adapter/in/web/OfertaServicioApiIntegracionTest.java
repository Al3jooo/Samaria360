package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.catalog.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.PruebaIntegracion;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.TokensDePrueba;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.SolicitudesServicio.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Iteración I en ms-catalog (DEC-311, DEC-313) contra MongoDB (M017) y RabbitMQ reales: el historial de
 * modificaciones (RF41) con los precios de RF32 y los cambios de {@code OfertaModificada} de ms-booking (RF19, con la
 * plantilla que fija la oferta), y la galería del servicio aprobado (RF48).
 */
@PruebaIntegracion
class OfertaServicioApiIntegracionTest {

    private static final String RUTA = "/api/v1/catalogo/prestador";
    private static final String EXCHANGE = "samaria360.eventos.v1";
    private static final String CLAVE = "booking.oferta.modificada";
    private static final Instant AHORA = Instant.parse("2026-11-10T15:00:00Z");
    private static final byte[] PNG = Arrays.copyOf(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 64);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private RelojFijo reloj;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private ObjectMapper mapper;

    private final UUID prestador = UUID.randomUUID();
    private final UUID otro = UUID.randomUUID();
    private final UUID administrador = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        reloj.ajustar(AHORA);
        filaDeCuenta(prestador, "Prestador", "RNT-OFE-1");
        filaDeCuenta(otro, "Prestador", "RNT-OFE-2");
        filaDeCuenta(administrador, "Administrador", null);
    }

    @AfterEach
    void limpiar() {
        for (UUID id : List.of(prestador, otro, administrador)) {
            mongo.getCollection("servicios").deleteMany(Filters.eq("prestadorId", id.toString()));
            mongo.getCollection("historial_modificaciones").deleteMany(Filters.eq("prestadorId", id.toString()));
            mongo.getCollection("cuentas").deleteOne(Filters.eq("_id", id.toString()));
        }
    }

    // ---- RF41 ----------------------------------------------------------------------------------------------------

    @Test
    void elCambioDePrecioApareceEnElHistorialDelPrestadorYNoEnElDeOtro() throws Exception {
        String id = registrar(SolicitudesServicio.tour("Tour historial " + UUID.randomUUID()));

        MvcResult edicion = mvc.perform(put(RUTA + "/servicios/" + id + "/precio").header(HttpHeaders.AUTHORIZATION,
                token(prestador)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("precioBase", 120000, "version", 0)))).andReturn();
        assertThat(edicion.getResponse().getStatus()).isEqualTo(200);

        String historial = cuerpo(mvc.perform(get(RUTA + "/historial-modificaciones")
                .header(HttpHeaders.AUTHORIZATION, token(prestador))).andReturn());
        assertThat(JsonPath.<Integer>read(historial, "$.totalElementos")).isEqualTo(1);
        assertThat(JsonPath.<String>read(historial, "$.elementos[0].tipo")).isEqualTo("Precio");
        assertThat(JsonPath.<String>read(historial, "$.elementos[0].valorAnterior")).isEqualTo("95000");
        assertThat(JsonPath.<String>read(historial, "$.elementos[0].valorNuevo")).isEqualTo("120000");
        assertThat(JsonPath.<String>read(historial, "$.elementos[0].idServicio")).isEqualTo(id);
        assertThat(JsonPath.<String>read(historial, "$.elementos[0].fecha")).isEqualTo("2026-11-10T15:00:00Z");

        String ajeno = cuerpo(mvc.perform(get(RUTA + "/historial-modificaciones")
                .header(HttpHeaders.AUTHORIZATION, token(otro))).andReturn());
        assertThat(JsonPath.<Integer>read(ajeno, "$.totalElementos")).isZero();
    }

    @Test
    void unFiltroInvalidoEs400YOtroRolEs403() throws Exception {
        assertThat(mvc.perform(get(RUTA + "/historial-modificaciones").param("servicio", "x")
                .header(HttpHeaders.AUTHORIZATION, token(prestador))).andReturn().getResponse().getStatus())
                .isEqualTo(400);
        assertThat(mvc.perform(get(RUTA + "/historial-modificaciones").header(HttpHeaders.AUTHORIZATION,
                TokensDePrueba.bearer(TokensDePrueba.usuario(administrador, "Administrador", 1)))).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get(RUTA + "/historial-modificaciones")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
    }

    // ---- RF19 desde ms-booking -----------------------------------------------------------------------------------

    @Test
    void losCambiosDeOfertaDeMsBookingLleganAlHistorialYLaPlantillaFijaLaOferta() throws Exception {
        String id = registrar(SolicitudesServicio.tour("Tour plantilla " + UUID.randomUUID()));
        String idCupos = UUID.randomUUID().toString();

        publicar(idCupos, id, prestador, "Cupos", Map.of("fecha", "2026-11-20", "horaInicio", "08:00",
                "valorAnterior", "12", "valorNuevo", "8"));
        publicar(idCupos, id, prestador, "Cupos", Map.of("fecha", "2026-11-20", "horaInicio", "08:00",
                "valorAnterior", "12", "valorNuevo", "8"));
        publicar(UUID.randomUUID().toString(), id, prestador, "Plantilla", Map.of(
                "valorAnterior", "Sabado, Domingo · 08:00", "valorNuevo", "Lunes · 07:00, 14:00",
                "diasOperacion", List.of("Lunes"), "salidas", List.of("07:00", "14:00")));
        // De un servicio ajeno: se ignora.
        publicar(UUID.randomUUID().toString(), id, otro, "Cierre", Map.of("fecha", "2026-11-21",
                "horaInicio", "08:00", "valorAnterior", "12", "valorNuevo", "0"));

        await().atMost(Duration.ofSeconds(20)).until(() ->
                mongo.getCollection("historial_modificaciones").countDocuments(Filters.eq("servicioId", id)) == 2
                        && List.of("Lunes").equals(documento(id).get("oferta", Document.class).get("diasOperacion")));
        Document oferta = documento(id).get("oferta", Document.class);
        assertThat(oferta.getList("salidas", String.class)).containsExactly("07:00", "14:00");

        String historial = cuerpo(mvc.perform(get(RUTA + "/historial-modificaciones").param("servicio", id)
                .header(HttpHeaders.AUTHORIZATION, token(prestador))).andReturn());
        assertThat(JsonPath.<List<String>>read(historial, "$.elementos[*].tipo"))
                .containsExactlyInAnyOrder("Cupos", "Plantilla");
        assertThat(JsonPath.<List<String>>read(historial, "$.elementos[?(@.tipo == 'Cupos')].detalle"))
                .containsExactly("2026-11-20 08:00");
    }

    @Test
    void unCambioDeOfertaFueraDelContratoVaALaColaDeMensajesFallidos() throws Exception {
        RabbitAdmin admin = new RabbitAdmin(rabbit);
        long antes = admin.getQueueInfo("ms-catalog.eventos.v1.dlq").getMessageCount();

        publicar(UUID.randomUUID().toString(), "65a0000000000000000000ff", prestador, "Desconocido",
                Map.of("valorNuevo", "1"));

        await().atMost(Duration.ofSeconds(15)).until(() ->
                admin.getQueueInfo("ms-catalog.eventos.v1.dlq").getMessageCount() == antes + 1);
    }

    // ---- RF48 ----------------------------------------------------------------------------------------------------

    @Test
    void elPrestadorAgregaHastaCincoImagenesYQuitaSinDejarLaGaleriaVacia() throws Exception {
        String id = registrar(SolicitudesServicio.tour("Tour galería " + UUID.randomUUID()));
        assertThat(subir(id, prestador).getResponse().getStatus()).isEqualTo(409); // Pendiente
        aprobar(id);

        List<String> galeria = List.of();
        for (int i = 0; i < 5; i++) {
            MvcResult r = subir(id, prestador);
            assertThat(r.getResponse().getStatus()).isEqualTo(201);
            galeria = JsonPath.read(cuerpo(r), "$.imagenes");
        }
        assertThat(galeria).hasSize(5);
        MvcResult sexta = subir(id, prestador);
        assertThat(sexta.getResponse().getStatus()).isEqualTo(409);
        assertThat(JsonPath.<String>read(cuerpo(sexta), "$.codigo")).isEqualTo("LIMITE_IMAGENES");

        String primera = galeria.get(0);
        assertThat(mvc.perform(get(primera)).andReturn().getResponse().getStatus()).isEqualTo(200);
        String publico = cuerpo(mvc.perform(get("/api/v1/catalogo/servicios/" + id)).andReturn());
        assertThat(JsonPath.<List<String>>read(publico, "$.imagenes")).containsExactlyElementsOf(galeria);

        for (int i = 0; i < 4; i++) {
            MvcResult r = quitar(id, galeria.get(i), prestador);
            assertThat(r.getResponse().getStatus()).isEqualTo(200);
        }
        assertThat(mvc.perform(get(primera)).andReturn().getResponse().getStatus()).isEqualTo(404);
        MvcResult ultima = quitar(id, galeria.get(4), prestador);
        assertThat(ultima.getResponse().getStatus()).isEqualTo(409);
        assertThat(JsonPath.<String>read(cuerpo(ultima), "$.codigo")).isEqualTo("IMAGEN_MINIMA");
        assertThat(documento(id).getList("imagenes", String.class)).containsExactly(galeria.get(4));
    }

    @Test
    void laGaleriaDeOtroPrestadorEs404YUnaImagenInvalidaEs400() throws Exception {
        String id = registrar(SolicitudesServicio.tour("Tour galería ajena " + UUID.randomUUID()));
        aprobar(id);

        assertThat(subir(id, otro).getResponse().getStatus()).isEqualTo(404);
        MvcResult gif = mvc.perform(multipart(RUTA + "/servicios/" + id + "/imagenes")
                .file(new MockMultipartFile("imagen", "a.gif", "image/gif", new byte[]{'G', 'I', 'F', '8'}))
                .header(HttpHeaders.AUTHORIZATION, token(prestador))).andReturn();
        assertThat(gif.getResponse().getStatus()).isEqualTo(400);
        assertThat(quitar(id, "/api/v1/catalogo/imagenes/" + new ObjectId(), prestador).getResponse().getStatus())
                .isEqualTo(400);
    }

    // ---- ayudas --------------------------------------------------------------------------------------------------

    private void publicar(String idEvento, String idServicio, UUID idPrestador, String tipo,
                          Map<String, Object> extra) throws Exception {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idServicio", idServicio);
        datos.put("idPrestador", idPrestador.toString());
        datos.put("tipo", tipo);
        datos.putAll(extra);
        String cuerpo = mapper.writeValueAsString(Map.of("idEvento", idEvento, "tipo", "OfertaModificada",
                "version", 1, "origen", "ms-booking", "ocurridoEn", AHORA.toString(), "clave", idServicio,
                "datos", datos));
        rabbit.send(EXCHANGE, CLAVE, MessageBuilder.withBody(cuerpo.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON).build());
    }

    private MvcResult subir(String id, UUID quien) throws Exception {
        return mvc.perform(multipart(RUTA + "/servicios/" + id + "/imagenes")
                .file(new MockMultipartFile("imagen", "playa.png", "image/png", PNG))
                .header(HttpHeaders.AUTHORIZATION, token(quien))).andReturn();
    }

    private MvcResult quitar(String id, String ruta, UUID quien) throws Exception {
        return mvc.perform(delete(RUTA + "/servicios/" + id + "/imagenes").param("ruta", ruta)
                .header(HttpHeaders.AUTHORIZATION, token(quien))).andReturn();
    }

    private String registrar(Map<String, Object> cuerpo) throws Exception {
        MvcResult resultado = mvc.perform(post(RUTA + "/servicios").header(HttpHeaders.AUTHORIZATION, token(prestador))
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo))).andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
        return JsonPath.read(cuerpo(resultado), "$.id");
    }

    private void aprobar(String id) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/catalogo/admin/servicios/" + id + "/aprobacion")
                .header(HttpHeaders.AUTHORIZATION,
                        TokensDePrueba.bearer(TokensDePrueba.usuario(administrador, "Administrador", 1)))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("decision", "Aprobar")))).andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
    }

    private Document documento(String id) {
        return mongo.getCollection("servicios").find(Filters.eq("_id", new ObjectId(id))).first();
    }

    private static String token(UUID id) {
        return TokensDePrueba.bearer(TokensDePrueba.usuario(id, "Prestador", 1));
    }

    private void filaDeCuenta(UUID id, String rol, String rnt) {
        Document cuenta = new Document("_id", id.toString()).append("rol", rol).append("estadoCuenta", "Activo")
                .append("version", 1L).append("actualizadoEn", new Date());
        if (rnt != null) {
            cuenta.append("rnt", rnt);
        }
        mongo.getCollection("cuentas").insertOne(cuenta);
    }

    private static String cuerpo(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
