package co.edu.unimagdalena.samaria360.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de carga de RNF03 (DEC-277): con 500 usuarios, un tropiezo de ~1 s llenaba el bulkhead por defecto de
 * Spring Cloud CircuitBreaker (25 llamadas simultáneas por ruta, sin espera); sus rechazos abrían el circuito y la
 * ruta respondía 503 durante 10 s. El bulkhead está desactivado: con 40 peticiones simultáneas a un servicio que
 * tarda 500 ms, todas pasan. Solo el timeout de respuesta y el circuit breaker (502/503/504) cortan una ruta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrenciaRutasTest {

    static final int SIMULTANEAS = 40;

    static final ServicioSimulado CATALOGO = ServicioSimulado.iniciar("ms-catalog")
            .responder(200, "{\"elementos\":[]}")
            .demorar(Duration.ofMillis(500));

    @DynamicPropertySource
    static void destinos(DynamicPropertyRegistry registro) {
        registro.add("CATALOG_URI", CATALOGO::uri);
    }

    @AfterAll
    static void detener() {
        CATALOGO.close();
    }

    @LocalServerPort
    private int puerto;

    @Test
    void masDeVeinticincoPeticionesSimultaneasNoSeRechazan() {
        WebClient cliente = WebClient.create("http://127.0.0.1:" + puerto);

        List<Integer> estados = Flux.range(0, SIMULTANEAS)
                .flatMap(i -> cliente.get().uri("/api/v1/catalogo/oferta")
                        .exchangeToMono(r -> r.releaseBody().thenReturn(r.statusCode().value())), SIMULTANEAS)
                .collectList()
                .block(Duration.ofSeconds(15));

        assertThat(estados).hasSize(SIMULTANEAS).containsOnly(200);
        assertThat(CATALOGO.solicitudes()).hasSize(SIMULTANEAS);
    }
}
