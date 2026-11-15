package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.auth.infrastructure.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * CU Registrarse (RF01, RF02) de punta a punta: MockMvc + Spring Security + PostgreSQL (Testcontainers) con BCrypt real.
 * Cada prueba usa correos y RNT propios: el contexto y la base se comparten entre clases.
 */
@PruebaIntegracion
@ExtendWith(OutputCaptureExtension.class)
class RegistroApiIntegracionTest {

    private static final String TURISTA = "/api/v1/auth/registro/turista";
    private static final String PRESTADOR = "/api/v1/auth/registro/prestador";
    private static final String CLAVE = "Clave1234";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // ------------------------------------------------------------------ registro correcto

    @Test
    void unTuristaQuedaActivoConSuSubtipoYSusCategorias() throws Exception {
        String correo = correoUnico();

        MvcResult resultado = registrarTurista(correo.toUpperCase(), "co", "[\"Naturaleza\",\"Aventura\"]", true);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(202);
        assertThat(cuerpo(resultado)).isEqualTo("{\"estado\":\"RECIBIDO\"}");
        assertThat(resultado.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        Map<String, Object> usuario = jdbc.queryForMap("SELECT u.id_usuario, u.correo, u.estado_cuenta::text AS estado,"
                + " u.intentos_fallidos, r.nombre AS rol FROM usuario u JOIN rol r ON r.id_rol = u.id_rol"
                + " WHERE u.correo = ?", correo);
        assertThat(usuario).containsEntry("estado", "Activo").containsEntry("rol", "Turista")
                .containsEntry("intentos_fallidos", 0);
        UUID id = (UUID) usuario.get("id_usuario");
        assertThat(jdbc.queryForMap("SELECT pais_origen, acepto_politicas FROM turista WHERE id_usuario = ?", id))
                .containsEntry("pais_origen", "CO").containsEntry("acepto_politicas", true);
        assertThat(jdbc.queryForList("SELECT categoria FROM turista_categoria_interes WHERE id_usuario = ?",
                String.class, id)).containsExactlyInAnyOrder("Naturaleza", "Aventura");
    }

    @Test
    void unPrestadorQuedaPendienteConUnSoloCorreoEnLasDosColumnas() throws Exception {
        String correo = correoUnico();
        String rnt = rntUnico();

        MvcResult resultado = registrarPrestador(correo.toUpperCase(), rnt, null, true);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(202);
        Map<String, Object> fila = jdbc.queryForMap("SELECT u.correo, u.estado_cuenta::text AS estado, p.correo_corporativo,"
                + " p.estado_validacion, p.razon_social, p.acepto_politicas FROM usuario u"
                + " JOIN prestador p ON p.id_usuario = u.id_usuario WHERE p.rnt = ?", rnt);
        assertThat(fila).containsEntry("correo", correo).containsEntry("correo_corporativo", correo)
                .containsEntry("estado", "Activo").containsEntry("estado_validacion", "Pendiente")
                .containsEntry("acepto_politicas", true);
        assertThat(fila.get("razon_social")).isNull();
    }

    @Test
    void elTuristaRegistradoConMayusculasIniciaSesionConElCorreoEnMinusculas() throws Exception {
        String correo = correoUnico();
        registrarTurista("  " + correo.toUpperCase() + " ", "CO", "[\"Cultura\"]", true);

        MvcResult login = login(correo, CLAVE);

        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(cuerpo(login), "$.rol")).isEqualTo("Turista");
    }

    @Test
    void elPrestadorRegistradoNoIniciaSesionHastaQueLoValiden() throws Exception {
        String correo = correoUnico();
        registrarPrestador(correo, rntUnico(), "Tours S.A.S.", true);

        MvcResult login = login(correo, CLAVE);

        assertThat(login.getResponse().getStatus()).isEqualTo(403);
        assertThat(JsonPath.<String>read(cuerpo(login), "$.codigo")).isEqualTo("CUENTA_PENDIENTE_VALIDACION");
    }

    @Test
    void seGuardanLosValoresDeLongitudMaximaDeCadaColumna() throws Exception {
        String cincuenta = "n".repeat(50);
        String correoTurista = "t" + "c".repeat(58) + UUID.randomUUID() + "@x.co";
        assertThat(correoTurista).hasSize(100);

        MvcResult turista = mvc.perform(post(TURISTA).contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                "primerNombre", cincuenta, "segundoNombre", cincuenta, "primerApellido", cincuenta,
                "segundoApellido", cincuenta, "correo", correoTurista, "contrasena", CLAVE, "paisOrigen", "CO",
                "categoriasInteres", List.of("Cultura"), "aceptoPoliticas", true)))).andReturn();
        String rnt = (rntUnico() + "r".repeat(50)).substring(0, 50);
        String correoPrestador = "p" + "c".repeat(58) + UUID.randomUUID() + "@x.co";
        MvcResult prestador = mvc.perform(post(PRESTADOR).contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                "nombreRepresentanteLegal", "r".repeat(150), "razonSocial", "s".repeat(150), "rnt", rnt,
                "correoCorporativo", correoPrestador, "telefono", "1".repeat(20), "contrasena", CLAVE,
                "aceptoPoliticas", true)))).andReturn();

        assertThat(turista.getResponse().getStatus()).as(cuerpo(turista)).isEqualTo(202);
        assertThat(prestador.getResponse().getStatus()).as(cuerpo(prestador)).isEqualTo(202);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE correo IN (?, ?)", Integer.class,
                correoTurista, correoPrestador)).isEqualTo(2);
    }

    // ------------------------------------------------------------------ correo duplicado (ADR-006)

    @Test
    void elRegistroConCorreoExistenteRespondeExactamenteIgualQueUnoNuevo(CapturedOutput salida) throws Exception {
        String correo = correoUnico();

        MockHttpServletResponse nuevo = registrarTurista(correo, "CO", "[\"Cultura\"]", true).getResponse();
        MockHttpServletResponse repetido = registrarTurista(correo.toUpperCase(), "ES", "[\"Aventura\"]", true)
                .getResponse();

        assertThat(repetido.getStatus()).isEqualTo(nuevo.getStatus()).isEqualTo(202);
        assertThat(encabezados(repetido)).isEqualTo(encabezados(nuevo));
        assertThat(repetido.getContentAsString()).isEqualTo(nuevo.getContentAsString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE correo = ?", Integer.class, correo))
                .isEqualTo(1);
        // Se avisa al dueño por correo (DEC-278, RF34); el log lleva su id, nunca la dirección (dato personal). El envío
        // es asíncrono: se espera a que termine.
        UUID id = jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE correo = ?", UUID.class, correo);
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(salida.getOut()).contains("idUsuario=" + id));
        assertThat(salida.getOut()).doesNotContain(correo);
    }

    @Test
    void unPrestadorConCorreoExistenteYRntNuevoRecibe202SinCrearNada() throws Exception {
        String correo = correoUnico();
        registrarTurista(correo, "CO", "[\"Cultura\"]", true);
        String rnt = rntUnico();

        MvcResult resultado = registrarPrestador(correo, rnt, null, true);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(202);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM prestador WHERE rnt = ?", Integer.class, rnt)).isZero();
    }

    @Test
    void unRntExistenteResponde409ConCorreoNuevoYConCorreoExistente() throws Exception {
        String rnt = rntUnico();
        String correo = correoUnico();
        registrarPrestador(correo, rnt, null, true);

        MvcResult conCorreoNuevo = registrarPrestador(correoUnico(), rnt, null, true);
        MvcResult conCorreoExistente = registrarPrestador(correo, rnt, null, true);

        for (MvcResult resultado : List.of(conCorreoNuevo, conCorreoExistente)) {
            assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
            assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("RNT_YA_REGISTRADO");
        }
    }

    // ------------------------------------------------------------------ errores de validación

    @Test
    void losDatosInvalidosRespondenUnCodigoPorCampoConLosNombresJson() throws Exception {
        MvcResult resultado = mvc.perform(post(TURISTA).contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                "primerNombre", "", "primerApellido", "x".repeat(51), "correo", "sin-arroba", "contrasena", "corta",
                "paisOrigen", "XX", "categoriasInteres", List.of("Cultura", "Cultura"), "aceptoPoliticas", true))))
                .andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        String cuerpo = cuerpo(resultado);
        assertThat(JsonPath.<String>read(cuerpo, "$.codigo")).isEqualTo("DATOS_INVALIDOS");
        assertThat(JsonPath.<Map<String, String>>read(cuerpo, "$.campos")).containsOnly(
                Map.entry("primerNombre", "REQUERIDO"),
                Map.entry("primerApellido", "LONGITUD_MAXIMA"),
                Map.entry("correo", "FORMATO"),
                Map.entry("contrasena", "LONGITUD_MINIMA"),
                Map.entry("paisOrigen", "NO_PERMITIDO"),
                Map.entry("categoriasInteres", "DUPLICADO"));
    }

    @Test
    void sinAceptarLasPoliticasRespondeElMismoCodigoParaLosDosRoles() throws Exception {
        MvcResult turista = registrarTurista(correoUnico(), "CO", "[\"Cultura\"]", false);
        MvcResult prestador = registrarPrestador(correoUnico(), rntUnico(), null, false);

        for (MvcResult resultado : List.of(turista, prestador)) {
            assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
            assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("POLITICAS_NO_ACEPTADAS");
        }
    }

    @Test
    void unJsonMalFormadoRespondeDatosInvalidos() throws Exception {
        MvcResult resultado = mvc.perform(post(TURISTA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"aceptoPoliticas\": \"quizas\"")).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("DATOS_INVALIDOS");
    }

    // ------------------------------------------------------------------ transacción y carreras

    /** Si falla la inserción del subtipo, no queda el usuario: un trigger de prueba hace fallar el INSERT en turista. */
    @Test
    void siFallaElSubtipoNoQuedaUnUsuarioAMedias() throws Exception {
        String correo = correoUnico();
        jdbc.execute("CREATE OR REPLACE FUNCTION prueba_falla_subtipo() RETURNS trigger AS $$ BEGIN"
                + " IF NEW.primer_nombre = 'FallaSubtipo' THEN RAISE EXCEPTION 'falla simulada del subtipo'; END IF;"
                + " RETURN NEW; END $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER prueba_falla_subtipo BEFORE INSERT ON turista FOR EACH ROW"
                + " EXECUTE FUNCTION prueba_falla_subtipo()");
        try {
            MvcResult resultado = mvc.perform(post(TURISTA).contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                    "primerNombre", "FallaSubtipo", "primerApellido", "Pérez", "correo", correo, "contrasena", CLAVE,
                    "paisOrigen", "CO", "categoriasInteres", List.of("Cultura"), "aceptoPoliticas", true))))
                    .andReturn();

            assertThat(resultado.getResponse().getStatus()).isEqualTo(500);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE correo = ?", Integer.class, correo))
                    .isZero();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS prueba_falla_subtipo ON turista");
            jdbc.execute("DROP FUNCTION IF EXISTS prueba_falla_subtipo()");
        }
    }

    /**
     * Dos registros simultáneos con el mismo correo. Un trigger de prueba retrasa el INSERT en usuario para que los dos
     * pasen la comprobación del correo antes de que alguno confirme: el segundo choca con uq_usuario_correo, se revierte
     * entero, se reintenta y responde lo mismo. Nunca un 500, una sola cuenta y ninguna fila huérfana.
     */
    @Test
    void dosRegistrosSimultaneosConElMismoCorreoDanDos202YUnaSolaCuenta() throws Exception {
        String correo = correoUnico();
        jdbc.execute("CREATE OR REPLACE FUNCTION prueba_registro_lento() RETURNS trigger AS $$ BEGIN"
                + " IF NEW.correo = '" + correo + "' THEN PERFORM pg_sleep(1); END IF; RETURN NEW; END $$"
                + " LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER prueba_registro_lento BEFORE INSERT ON usuario FOR EACH ROW"
                + " EXECUTE FUNCTION prueba_registro_lento()");
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<MvcResult>> envios = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                envios.add(hilos.submit(() -> {
                    salida.await();
                    return registrarTurista(correo, "CO", "[\"Cultura\"]", true);
                }));
            }
            salida.countDown();

            for (Future<MvcResult> envio : envios) {
                MvcResult resultado = envio.get(30, TimeUnit.SECONDS);
                assertThat(resultado.getResponse().getStatus()).as(cuerpo(resultado)).isEqualTo(202);
                assertThat(cuerpo(resultado)).isEqualTo("{\"estado\":\"RECIBIDO\"}");
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE correo = ?", Integer.class, correo))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM turista t LEFT JOIN usuario u"
                    + " ON u.id_usuario = t.id_usuario WHERE u.id_usuario IS NULL", Integer.class)).isZero();
        } finally {
            hilos.shutdownNow();
            jdbc.execute("DROP TRIGGER IF EXISTS prueba_registro_lento ON usuario");
            jdbc.execute("DROP FUNCTION IF EXISTS prueba_registro_lento()");
        }
    }

    // ------------------------------------------------------------------ países (D5)

    @Test
    void losPaisesSonPublicosSoloCodigosYSePuedenGuardarEnCache() throws Exception {
        MvcResult resultado = mvc.perform(get("/api/v1/auth/paises")).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(resultado.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("max-age=86400, public");
        List<String> codigos = JsonPath.read(cuerpo(resultado), "$");
        assertThat(codigos).contains("CO", "ES").allMatch(codigo -> codigo.matches("[A-Z]{2}")).isSorted();
    }

    // ------------------------------------------------------------------ auxiliares

    private MvcResult registrarTurista(String correo, String pais, String categorias, boolean acepto) throws Exception {
        String cuerpo = "{\"primerNombre\":\"Ana\",\"primerApellido\":\"Pérez\",\"correo\":\"" + correo
                + "\",\"contrasena\":\"" + CLAVE + "\",\"paisOrigen\":\"" + pais + "\",\"categoriasInteres\":"
                + categorias + ",\"aceptoPoliticas\":" + acepto + "}";
        return mvc.perform(post(TURISTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andReturn();
    }

    private MvcResult registrarPrestador(String correo, String rnt, String razonSocial, boolean acepto)
            throws Exception {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nombreRepresentanteLegal", "Carlos Gómez");
        datos.put("razonSocial", razonSocial);
        datos.put("rnt", rnt);
        datos.put("correoCorporativo", correo);
        datos.put("telefono", "+57 3000000000");
        datos.put("contrasena", CLAVE);
        datos.put("aceptoPoliticas", acepto);
        return mvc.perform(post(PRESTADOR).contentType(MediaType.APPLICATION_JSON).content(json(datos))).andReturn();
    }

    private MvcResult login(String correo, String contrasena) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("correo", correo, "contrasena", contrasena)))).andReturn();
    }

    /** Todos los encabezados de la respuesta, sin Date (cambia en cada respuesta). */
    private static Map<String, List<String>> encabezados(MockHttpServletResponse respuesta) {
        Map<String, List<String>> todos = new LinkedHashMap<>();
        for (String nombre : respuesta.getHeaderNames()) {
            if (!nombre.equalsIgnoreCase(HttpHeaders.DATE)) {
                todos.put(nombre, respuesta.getHeaders(nombre));
            }
        }
        return todos;
    }

    private static String correoUnico() {
        return "registro-" + UUID.randomUUID() + "@samaria360.test";
    }

    private static String rntUnico() {
        return "RNT-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String json(Map<String, ?> datos) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(datos);
    }

    private static String cuerpo(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
