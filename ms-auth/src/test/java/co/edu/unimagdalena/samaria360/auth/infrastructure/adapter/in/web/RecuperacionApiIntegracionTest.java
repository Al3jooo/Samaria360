package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.auth.infrastructure.PruebaIntegracion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * CU Recuperar contraseña (RF34) de punta a punta: MockMvc + PostgreSQL + Mailpit (Testcontainers). El token se lee del
 * correo, como lo haría el usuario. Cada prueba registra su propio turista: no se usan las credenciales de la semilla.
 */
@PruebaIntegracion
class RecuperacionApiIntegracionTest {

    private static final String RECUPERACION = "/api/v1/auth/recuperacion";
    private static final String RESTABLECIMIENTO = "/api/v1/auth/recuperacion/restablecimiento";
    private static final Pattern TOKEN = Pattern.compile("/restablecer#token=([A-Za-z0-9_-]{43})");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Value("${prueba.mailpit.api}")
    private String mailpit;

    @Test
    void elEnlaceDelCorreoRestableceLaContrasenaQuitaElBloqueoYCierraLasSesiones() throws Exception {
        String correo = turistaNuevo("ClaveVieja1");
        UUID id = jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE correo = ?", UUID.class, correo);
        // Cuenta bloqueada por RF15 antes de recuperar.
        jdbc.update("UPDATE usuario SET intentos_fallidos = 3, bloqueado_hasta = ? WHERE id_usuario = ?",
                Timestamp.from(Instant.now().plus(Duration.ofMinutes(15))), id);
        long version = jdbc.queryForObject("SELECT version_cuenta FROM usuario WHERE id_usuario = ?", Long.class, id);

        MockHttpServletResponse pedido = pedir(correo, "es");
        assertThat(pedido.getStatus()).isEqualTo(202);
        assertThat(pedido.getContentAsString()).isEqualTo("{\"estado\":\"RECIBIDO\"}");
        String token = tokenDelCorreo(correo);
        // Solo el hash queda en la base.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM token_recuperacion WHERE token_hash = ?", Integer.class,
                token)).isZero();

        assertThat(restablecer(token, "ClaveNueva1").getStatus()).isEqualTo(204);

        assertThat(jdbc.queryForMap("SELECT intentos_fallidos, bloqueado_hasta, version_cuenta FROM usuario"
                + " WHERE id_usuario = ?", id))
                .containsEntry("intentos_fallidos", 0).containsEntry("bloqueado_hasta", null)
                .containsEntry("version_cuenta", version + 1);
        assertThat(jdbc.queryForList("""
                SELECT en.destino FROM outbox_entrega en JOIN outbox_evento e ON e.id_evento = en.id_evento
                WHERE e.id_usuario = ? AND e.tipo = 'CuentaActualizada' AND (e.carga ->> 'version')::bigint = ?
                """, String.class, id, version + 1)).containsExactlyInAnyOrder("ms-catalog", "ms-booking", "rabbitmq");
        assertThat(login(correo, "ClaveNueva1")).isEqualTo(200);
        assertThat(login(correo, "ClaveVieja1")).isEqualTo(401);
    }

    @Test
    void elEnlaceSirveUnaSolaVez() throws Exception {
        String correo = turistaNuevo("ClaveVieja1");
        pedir(correo, "en");
        String token = tokenDelCorreo(correo);
        assertThat(restablecer(token, "ClaveNueva1").getStatus()).isEqualTo(204);

        MockHttpServletResponse otraVez = restablecer(token, "OtraClave22");

        assertThat(otraVez.getStatus()).isEqualTo(400);
        assertThat(json.readTree(otraVez.getContentAsString()).get("codigo").asText()).isEqualTo("TOKEN_INVALIDO");
    }

    @Test
    void unEnlaceVencidoEsTokenInvalido() throws Exception {
        String correo = turistaNuevo("ClaveVieja1");
        pedir(correo, "es");
        String token = tokenDelCorreo(correo);
        jdbc.update("UPDATE token_recuperacion SET expira_en = now() - interval '1 minute'"
                + " WHERE id_usuario = (SELECT id_usuario FROM usuario WHERE correo = ?)", correo);

        assertThat(restablecer(token, "ClaveNueva1").getStatus()).isEqualTo(400);
        assertThat(login(correo, "ClaveVieja1")).isEqualTo(200);
    }

    /** ADR-006: la respuesta no revela si el correo tiene cuenta. */
    @Test
    void unCorreoSinCuentaRecibeLaMismaRespuestaYNoSeEnviaNada() throws Exception {
        String sinCuenta = "nadie-" + UUID.randomUUID() + "@samaria360.test";

        MockHttpServletResponse respuesta = pedir(sinCuenta, "es");

        assertThat(respuesta.getStatus()).isEqualTo(202);
        assertThat(respuesta.getContentAsString()).isEqualTo("{\"estado\":\"RECIBIDO\"}");
        Thread.sleep(500);
        assertThat(mensajesPara(sinCuenta).path("messages_count").asInt()).isZero();
    }

    @Test
    void unaContrasenaCortaEsDatosInvalidosYNoGastaElEnlace() throws Exception {
        String correo = turistaNuevo("ClaveVieja1");
        pedir(correo, "es");
        String token = tokenDelCorreo(correo);

        MockHttpServletResponse corta = restablecer(token, "corta");

        assertThat(corta.getStatus()).isEqualTo(400);
        assertThat(json.readTree(corta.getContentAsString()).at("/campos/contrasena").asText())
                .isEqualTo("LONGITUD_MINIMA");
        assertThat(restablecer(token, "ClaveNueva1").getStatus()).isEqualTo(204);
    }

    // ------------------------------------------------------------------ apoyo

    private String turistaNuevo(String clave) throws Exception {
        String correo = "recupera-" + UUID.randomUUID() + "@samaria360.test";
        mvc.perform(post("/api/v1/auth/registro/turista").contentType(MediaType.APPLICATION_JSON).content("""
                {"primerNombre":"Ana","primerApellido":"Rojas","correo":"%s","contrasena":"%s","paisOrigen":"CO",
                 "categoriasInteres":["Cultura"],"aceptoPoliticas":true}
                """.formatted(correo, clave))).andReturn();
        return correo;
    }

    private MockHttpServletResponse pedir(String correo, String idioma) throws Exception {
        return mvc.perform(post(RECUPERACION).contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\",\"idioma\":\"%s\"}".formatted(correo, idioma))).andReturn().getResponse();
    }

    private MockHttpServletResponse restablecer(String token, String contrasena) throws Exception {
        return mvc.perform(post(RESTABLECIMIENTO).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"contrasena\":\"%s\"}".formatted(token, contrasena)))
                .andReturn().getResponse();
    }

    private int login(String correo, String contrasena) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\",\"contrasena\":\"%s\"}".formatted(correo, contrasena)))
                .andReturn().getResponse().getStatus();
    }

    private JsonNode mensajesPara(String correo) throws Exception {
        String cuerpo = RestClient.create(mailpit).get()
                .uri(u -> u.path("/api/v1/search").queryParam("query", "to:" + correo).build())
                .retrieve().body(String.class);
        return json.readTree(cuerpo);
    }

    /** El correo se envía en otro hilo: se espera a que llegue a Mailpit y se lee el token del enlace. */
    private String tokenDelCorreo(String correo) {
        String[] token = new String[1];
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            JsonNode mensajes = mensajesPara(correo).path("messages");
            assertThat(mensajes.size()).isPositive();
            String id = mensajes.get(0).get("ID").asText();
            JsonNode mensaje = json.readTree(RestClient.create(mailpit).get().uri("/api/v1/message/{id}", id)
                    .retrieve().body(String.class));
            Matcher enlace = TOKEN.matcher(mensaje.get("Text").asText());
            assertThat(enlace.find()).isTrue();
            token[0] = enlace.group(1);
        });
        return token[0];
    }
}
