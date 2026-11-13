package co.edu.unimagdalena.samaria360.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Límite de tasa del login y del registro (DEC-271), con límites pequeños para la prueba. Cada prueba usa su propia
 * dirección en {@code X-Forwarded-For} para no compartir el conteo con las demás.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "samaria360.limite-tasa.login.maximo=2",
        "samaria360.limite-tasa.login.ventana=1m",
        "samaria360.limite-tasa.registro.maximo=1",
        "samaria360.limite-tasa.registro.ventana=10m"})
class LimiteTasaTest {

    static final ServicioSimulado AUTH = ServicioSimulado.iniciar("ms-auth").responder(200, "{\"token\":\"t\"}");
    static final ServicioSimulado CATALOGO = ServicioSimulado.iniciar("ms-catalog").responder(200, "{\"elementos\":[]}");
    static final ServicioSimulado RESERVAS = ServicioSimulado.iniciar("ms-booking").responder(200, "{}");

    @DynamicPropertySource
    static void destinos(DynamicPropertyRegistry registro) {
        registro.add("AUTH_URI", AUTH::uri);
        registro.add("CATALOG_URI", CATALOGO::uri);
        registro.add("BOOKING_URI", RESERVAS::uri);
    }

    @AfterAll
    static void detener() {
        AUTH.close();
        CATALOGO.close();
        RESERVAS.close();
    }

    @Autowired
    private WebTestClient cliente;

    @BeforeEach
    void limpiar() {
        AUTH.limpiar();
    }

    private WebTestClient.ResponseSpec login(String reenviado) {
        return cliente.post().uri("/api/v1/auth/login")
                .header("X-Forwarded-For", reenviado)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"correo\":\"a@b.co\",\"contrasena\":\"x\"}")
                .exchange();
    }

    @Test
    void pasadoElMaximoElLoginResponde429ConRetryAfterYNoLlegaAMsAuth() {
        login("10.0.0.1").expectStatus().isOk();
        login("10.0.0.1").expectStatus().isOk();

        login("10.0.0.1")
                .expectStatus().isEqualTo(429)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().exists("Retry-After")
                .expectHeader().exists("X-Correlation-Id")
                .expectBody()
                .jsonPath("$.status").isEqualTo(429)
                .jsonPath("$.codigo").isEqualTo("DEMASIADAS_SOLICITUDES");

        assertThat(AUTH.solicitudes()).hasSize(2);
    }

    @Test
    void cuentaLaUltimaDireccionDeXForwardedForQueEsLaQueAgregaNginx() {
        // El cliente puede inventar los valores anteriores; el último lo escribe nginx y no cambia.
        login("1.1.1.1, 10.0.0.2").expectStatus().isOk();
        login("2.2.2.2, 10.0.0.2").expectStatus().isOk();
        login("3.3.3.3, 10.0.0.2").expectStatus().isEqualTo(429);

        // Otra dirección real tiene su propio conteo.
        login("10.0.0.3").expectStatus().isOk();
    }

    @Test
    void elRegistroTieneSuPropioCupoYCubreTuristaYPrestador() {
        cliente.post().uri("/api/v1/auth/registro/turista").header("X-Forwarded-For", "10.0.0.4")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
                .expectStatus().isOk();
        cliente.post().uri("/api/v1/auth/registro/prestador").header("X-Forwarded-For", "10.0.0.4")
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange()
                .expectStatus().isEqualTo(429);

        // El login de la misma dirección no se ve afectado por el cupo del registro.
        login("10.0.0.4").expectStatus().isOk();
    }

    @Test
    void lasDemasRutasNoTienenLimite() {
        for (int i = 0; i < 5; i++) {
            cliente.get().uri("/api/v1/catalogo/oferta").header("X-Forwarded-For", "10.0.0.5").exchange()
                    .expectStatus().isOk();
            cliente.get().uri("/api/v1/auth/paises").header("X-Forwarded-For", "10.0.0.5").exchange()
                    .expectStatus().isOk();
        }
    }
}
