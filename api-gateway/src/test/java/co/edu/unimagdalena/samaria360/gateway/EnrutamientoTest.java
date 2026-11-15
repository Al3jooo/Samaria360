package co.edu.unimagdalena.samaria360.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Enrutamiento, Authorization intacta, 404, X-Correlation-Id, CORS y health, con los servicios disponibles. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EnrutamientoTest {

    private static final String CORRELACION = "X-Correlation-Id";
    private static final String ORIGEN_PERMITIDO = "http://localhost:5173";

    static final ServicioSimulado AUTH = ServicioSimulado.iniciar("ms-auth").responder(200, "{\"token\":\"t\"}");
    static final ServicioSimulado CATALOGO = ServicioSimulado.iniciar("ms-catalog").responder(200, "{\"elementos\":[]}");
    static final ServicioSimulado RESERVAS = ServicioSimulado.iniciar("ms-booking").responder(201, "{\"estado\":\"Confirmada\"}");
    static final ServicioSimulado ANALITICA = ServicioSimulado.iniciar("ms-analytics").responder(200, "{\"elementos\":[]}");
    static final ServicioSimulado ASISTENTE = ServicioSimulado.iniciar("ms-chatbot").responder(200, "{\"respuesta\":\"hola\"}");

    @DynamicPropertySource
    static void destinos(DynamicPropertyRegistry registro) {
        registro.add("AUTH_URI", AUTH::uri);
        registro.add("CATALOG_URI", CATALOGO::uri);
        registro.add("BOOKING_URI", RESERVAS::uri);
        registro.add("ANALYTICS_URI", ANALITICA::uri);
        registro.add("CHATBOT_URI", ASISTENTE::uri);
    }

    @AfterAll
    static void detener() {
        AUTH.close();
        CATALOGO.close();
        RESERVAS.close();
        ANALITICA.close();
        ASISTENTE.close();
    }

    @Autowired
    private WebTestClient cliente;

    @LocalServerPort
    private int puerto;

    @BeforeEach
    void limpiar() {
        AUTH.limpiar();
        CATALOGO.limpiar();
        RESERVAS.limpiar();
        ANALITICA.limpiar();
        ASISTENTE.limpiar();
    }

    /** DEC-297: el prefijo de ms-chatbot llega sin reescribir, con el cuerpo y el token. */
    @Test
    void elPrefijoDelAsistenteLlegaAMsChatbot() {
        cliente.post().uri("/api/v1/chatbot/mensajes").header(HttpHeaders.AUTHORIZATION, "Bearer a.b.c")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"mensaje\":\"hola\"}")
                .exchange().expectStatus().isOk();

        assertThat(ASISTENTE.ultima().uri()).isEqualTo("/api/v1/chatbot/mensajes");
        assertThat(ASISTENTE.ultima().cabecera(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer a.b.c");
    }

    /** DEC-292: el prefijo de ms-analytics llega sin reescribir, con su query string y el token. */
    @Test
    void elPrefijoDeAnaliticaLlegaAMsAnalytics() {
        cliente.get().uri("/api/v1/analytics/notificaciones?pagina=1").header(HttpHeaders.AUTHORIZATION, "Bearer x.y.z")
                .exchange().expectStatus().isOk();

        assertThat(ANALITICA.ultima().uri()).isEqualTo("/api/v1/analytics/notificaciones?pagina=1");
        assertThat(ANALITICA.ultima().cabecera(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer x.y.z");
    }

    @Test
    void elPrefijoDeAuthLlegaAMsAuthSinCambiosDeRutaNiCuerpo() {
        cliente.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"correo\":\"turista@samaria360.test\",\"contrasena\":\"x\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Servicio", "ms-auth")
                .expectBody().json("{\"token\":\"t\"}");

        assertThat(AUTH.solicitudes()).hasSize(1);
        assertThat(AUTH.ultima().metodo()).isEqualTo("POST");
        assertThat(AUTH.ultima().uri()).isEqualTo("/api/v1/auth/login");
        assertThat(AUTH.ultima().cuerpo()).isEqualTo("{\"correo\":\"turista@samaria360.test\",\"contrasena\":\"x\"}");
        assertThat(CATALOGO.solicitudes()).isEmpty();
    }

    @Test
    void elPrefijoDeCatalogoLlegaAMsCatalogConSuQueryString() {
        cliente.get().uri("/api/v1/catalogo/oferta?texto=cafe&pagina=1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Servicio", "ms-catalog");

        assertThat(CATALOGO.ultima().metodo()).isEqualTo("GET");
        assertThat(CATALOGO.ultima().uri()).isEqualTo("/api/v1/catalogo/oferta?texto=cafe&pagina=1");
        assertThat(AUTH.solicitudes()).isEmpty();
    }

    @Test
    void elPrefijoDeReservasLlegaAMsBookingConAuthorizationEIdempotencyKey() {
        String token = "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.firma-de-prueba";

        cliente.post().uri("/api/v1/reservas")
                .header(HttpHeaders.AUTHORIZATION, token)
                .header("Idempotency-Key", "8f14e45f-ceea-4672-a0a3-3b1d8a8f2e01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"idServicio\":\"x\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("X-Servicio", "ms-booking");

        assertThat(RESERVAS.ultima().uri()).isEqualTo("/api/v1/reservas");
        assertThat(RESERVAS.ultima().cabecera(HttpHeaders.AUTHORIZATION)).isEqualTo(token);
        assertThat(RESERVAS.ultima().cabecera("Idempotency-Key")).isEqualTo("8f14e45f-ceea-4672-a0a3-3b1d8a8f2e01");
        assertThat(AUTH.solicitudes()).isEmpty();
        assertThat(CATALOGO.solicitudes()).isEmpty();
    }

    @Test
    void un503PropioDeMsBookingLlegaTalCualConElServicioQueNoResponde() {
        RESERVAS.responder(503, "{\"codigo\":\"SERVICIO_NO_DISPONIBLE\",\"servicio\":\"ms-catalog\"}");
        try {
            cliente.post().uri("/api/v1/reservas").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                    .exchange()
                    .expectStatus().isEqualTo(503)
                    .expectHeader().valueEquals("X-Servicio", "ms-booking")
                    .expectBody().jsonPath("$.servicio").isEqualTo("ms-catalog");
        } finally {
            RESERVAS.responder(201, "{\"estado\":\"Confirmada\"}");
        }
    }

    @Test
    void laDisponibilidadDeReservasLlegaConSuQueryString() {
        cliente.get().uri("/api/v1/reservas/disponibilidad/resumen?servicios=a,b")
                .exchange()
                .expectHeader().valueEquals("X-Servicio", "ms-booking");

        assertThat(RESERVAS.ultima().uri()).isEqualTo("/api/v1/reservas/disponibilidad/resumen?servicios=a,b");
    }

    @Test
    void laCabeceraAuthorizationLlegaIntactaAlServicio() {
        String token = "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.firma-de-prueba";

        cliente.get().uri("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus().isOk();

        assertThat(AUTH.ultima().cabecera(HttpHeaders.AUTHORIZATION)).isEqualTo(token);
    }

    @Test
    void losErroresDeNegocioDelServicioPasanSinCambios() {
        AUTH.responder(401, "{\"codigo\":\"CREDENCIALES_INVALIDAS\"}");
        try {
            cliente.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.codigo").isEqualTo("CREDENCIALES_INVALIDAS");
        } finally {
            AUTH.responder(200, "{\"token\":\"t\"}");
        }
    }

    @Test
    void unaRutaDesconocidaDa404SinLlegarANingunServicio() {
        cliente.get().uri("/otra-cosa").exchange().expectStatus().isNotFound();
        cliente.get().uri("/api/v1/otro-servicio/x").exchange().expectStatus().isNotFound();

        assertThat(AUTH.solicitudes()).isEmpty();
        assertThat(CATALOGO.solicitudes()).isEmpty();
        assertThat(RESERVAS.solicitudes()).isEmpty();
    }

    /** ADR-008: los endpoints internos de ms-catalog y ms-booking no se exponen; solo los alcanza el relevo de ms-auth. */
    @Test
    void lasRutasInternasNoSeEnrutanANingunServicio() {
        for (String ruta : new String[]{"/internal/v1/cuentas/00000000-0000-4000-8000-000000000001",
                "/internal/v1/cuentas", "/api/v1/internal/v1/cuentas/1", "/internal/", "/internal"}) {
            cliente.put().uri(ruta).contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer servicio").bodyValue("{\"rol\":\"Turista\"}")
                    .exchange().expectStatus().value(estado -> assertThat(estado).as(ruta).isGreaterThanOrEqualTo(400));
        }
        cliente.put().uri("/internal/v1/cuentas/1").exchange().expectStatus().isNotFound();
        cliente.get().uri("/internal/v1/cuentas/1").exchange().expectStatus().isNotFound();

        assertThat(AUTH.solicitudes().stream().filter(s -> s.uri().contains("internal")).toList()).isEmpty();
        assertThat(CATALOGO.solicitudes().stream().filter(s -> s.uri().contains("internal")).toList()).isEmpty();
        assertThat(RESERVAS.solicitudes().stream().filter(s -> s.uri().contains("internal")).toList()).isEmpty();
    }

    /**
     * N8 (ADR-008): una ruta no normalizada se rechaza antes de enrutar. Sin esto, «/api/v1/reservas/../../internal/…»
     * coincide con el prefijo de reservas y llega tal cual a ms-booking. Se usa una URI absoluta para que el cliente
     * envíe la ruta sin normalizarla ni recodificar el «%».
     */
    @Test
    void unaRutaConPuntosOSeparadoresCodificadosEs400SinLlegarANingunServicio() {
        for (String ruta : new String[]{
                "/api/v1/reservas/../../internal/v1/cuentas/x",
                "/api/v1/reservas/./disponibilidad",
                "/api/v1/reservas/%2e%2e/%2e%2e/internal/v1/cuentas/x",
                "/api/v1/reservas/%2E%2E/%2E%2E/internal/v1/cuentas/x",
                "/api/v1/reservas/..%2f..%2finternal/v1/cuentas/x",
                "/api/v1/reservas/..%5c..%5cinternal/v1/cuentas/x"}) {
            cliente.put().uri(URI.create("http://localhost:" + puerto + ruta))
                    .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"rol\":\"Turista\"}")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectHeader().exists(CORRELACION)
                    .expectBody().jsonPath("$.codigo").isEqualTo("RUTA_INVALIDA")
                    .jsonPath("$.status").isEqualTo(400);
        }

        assertThat(AUTH.solicitudes()).isEmpty();
        assertThat(CATALOGO.solicitudes()).isEmpty();
        assertThat(RESERVAS.solicitudes()).isEmpty();
    }

    @Test
    void unSegmentoQueSoloEmpiezaConPuntoSigueSiendoValido() {
        cliente.get().uri("/api/v1/auth/.well-known/jwks.json").exchange().expectStatus().isOk();

        assertThat(AUTH.ultima().uri()).isEqualTo("/api/v1/auth/.well-known/jwks.json");
    }

    @Test
    void sinXCorrelationIdElGatewayLoGeneraLoReenviaYLoDevuelve() {
        EntityExchangeResult<byte[]> resultado = cliente.get().uri("/api/v1/catalogo/oferta")
                .exchange().expectStatus().isOk().expectBody().returnResult();

        String generado = resultado.getResponseHeaders().getFirst(CORRELACION);
        assertThat(UUID.fromString(generado)).isNotNull();
        assertThat(CATALOGO.ultima().cabecera(CORRELACION)).isEqualTo(generado);
    }

    /**
     * DEC-314: X-Forwarded-For (el de nginx) llega al servicio. ms-catalog cuenta solo las consultas que lo traen
     * (DEC-307); sin trusted-proxies el gateway lo quitaba y RF14 y RF31 quedaban sin datos.
     */
    @Test
    void elXForwardedForDeNginxLlegaAlServicio() {
        cliente.get().uri("/api/v1/catalogo/atractivos/6ad2be3e06a2d5c9d602cec9")
                .header("X-Forwarded-For", "203.0.113.7")
                .exchange()
                .expectStatus().isOk();

        // El gateway agrega la dirección del proxy de confianza que llamó (nginx; aquí, loopback). Las direcciones que
        // no son de confianza no se reenvían: a ms-catalog le basta con que la cabecera exista.
        assertThat(CATALOGO.ultima().cabecera("X-Forwarded-For")).isEqualTo("127.0.0.1");
    }

    @Test
    void unXCorrelationIdValidoSePropagaTalCual() {
        cliente.post().uri("/api/v1/auth/login").header(CORRELACION, "front-123.abc_XYZ")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(CORRELACION, "front-123.abc_XYZ");

        assertThat(AUTH.ultima().cabecera(CORRELACION)).isEqualTo("front-123.abc_XYZ");
    }

    @Test
    void unXCorrelationIdInvalidoSeReemplaza() {
        for (String invalido : new String[] {"con espacios", "a".repeat(65), "<script>"}) {
            String devuelto = cliente.get().uri("/api/v1/catalogo/oferta").header(CORRELACION, invalido)
                    .exchange().expectStatus().isOk().returnResult(String.class)
                    .getResponseHeaders().getFirst(CORRELACION);

            assertThat(devuelto).isNotEqualTo(invalido);
            assertThat(UUID.fromString(devuelto)).isNotNull();
            assertThat(CATALOGO.ultima().cabecera(CORRELACION)).isEqualTo(devuelto);
        }
    }

    @Test
    void elXCorrelationIdTambienVaEnLos404() {
        cliente.get().uri("/no-existe").exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists(CORRELACION);
    }

    @Test
    void elPreflightDeCorsDesdeElOrigenPermitidoAceptaAuthorization() {
        cliente.options().uri("/api/v1/auth/me")
                .header(HttpHeaders.ORIGIN, ORIGEN_PERMITIDO)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEN_PERMITIDO)
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        v -> assertThat(v).containsIgnoringCase("authorization"));

        // El gateway responde el preflight sin consultar al servicio.
        assertThat(AUTH.solicitudes()).isEmpty();
    }

    @Test
    void elPreflightDeReservarAceptaIdempotencyKey() {
        cliente.options().uri("/api/v1/reservas")
                .header(HttpHeaders.ORIGIN, ORIGEN_PERMITIDO)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,idempotency-key")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        v -> assertThat(v).containsIgnoringCase("idempotency-key"));

        assertThat(RESERVAS.solicitudes()).isEmpty();
    }

    @Test
    void unaReservaDesdeElOrigenPermitidoExponeIdempotentReplayedYLocation() {
        cliente.post().uri("/api/v1/reservas").header(HttpHeaders.ORIGIN, ORIGEN_PERMITIDO)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, v -> assertThat(v)
                        .containsIgnoringCase("Idempotent-Replayed").containsIgnoringCase("Location"));
    }

    @Test
    void elPreflightDesdeOtroOrigenSeRechaza() {
        cliente.options().uri("/api/v1/auth/me")
                .header(HttpHeaders.ORIGIN, "http://evil.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void unaPeticionDesdeElOrigenPermitidoExponeElXCorrelationId() {
        cliente.get().uri("/api/v1/catalogo/oferta").header(HttpHeaders.ORIGIN, ORIGEN_PERMITIDO)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEN_PERMITIDO)
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        v -> assertThat(v).containsIgnoringCase(CORRELACION));
    }

    @Test
    void elHealthEsPublico() {
        cliente.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
