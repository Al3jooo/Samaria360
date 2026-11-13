package co.edu.unimagdalena.samaria360.gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Límite de tasa del inicio de sesión y del registro (DEC-271; mitigación del riesgo residual de ADR-006). Cuenta, por
 * dirección del cliente y en una ventana fija, los {@code POST /api/v1/auth/login} y los
 * {@code POST /api/v1/auth/registro/**}; al pasar el máximo responde 429 {@code DEMASIADAS_SOLICITUDES} con
 * {@code Retry-After}, sin llegar a ms-auth. El bloqueo de RF15 (3 intentos por cuenta) sigue en ms-auth: este límite
 * frena a quien prueba muchas cuentas desde la misma conexión o crea cuentas en masa.
 * <p>
 * <b>Dirección del cliente:</b> el gateway solo se publica en 127.0.0.1 y el navegador llega por nginx, que agrega la
 * dirección real al final de {@code X-Forwarded-For} ({@code $proxy_add_x_forwarded_for}). Se toma el <b>último</b>
 * valor, el que escribió nginx; los anteriores los puede inventar el cliente. Sin el encabezado (Vite en desarrollo,
 * pruebas), la dirección remota de la conexión.
 * <p>
 * Corre después de {@link CorrelacionWebFilter} y de {@link RutaSeguraWebFilter}: el 429 lleva {@code X-Correlation-Id}.
 */
@Component
@EnableConfigurationProperties(LimiteTasaPropiedades.class)
public class LimiteTasaWebFilter implements WebFilter, Ordered {

    static final String CODIGO = "DEMASIADAS_SOLICITUDES";
    private static final Logger LOG = LoggerFactory.getLogger(LimiteTasaWebFilter.class);
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REGISTRO = "/api/v1/auth/registro/";

    private final LimiteTasaPropiedades propiedades;
    private final ObjectMapper json;
    private final ContadorVentanaFija contador = new ContadorVentanaFija(Clock.systemUTC());

    public LimiteTasaWebFilter(LimiteTasaPropiedades propiedades, ObjectMapper json) {
        this.propiedades = propiedades;
        this.json = json;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest peticion = exchange.getRequest();
        if (peticion.getMethod() != HttpMethod.POST) {
            return chain.filter(exchange);
        }
        String ruta = peticion.getURI().getRawPath();
        String grupo;
        LimiteTasaPropiedades.Regla regla;
        if (LOGIN.equals(ruta)) {
            grupo = "login";
            regla = propiedades.login();
        } else if (ruta != null && ruta.startsWith(REGISTRO)) {
            grupo = "registro";
            regla = propiedades.registro();
        } else {
            return chain.filter(exchange);
        }
        Optional<Duration> espera = contador.registrar(grupo + "|" + direccionCliente(peticion), regla);
        if (espera.isEmpty()) {
            return chain.filter(exchange);
        }
        LOG.warn("Límite de tasa de {} superado; 429 {} correlacion={}", grupo, CODIGO,
                peticion.getHeaders().getFirst(CorrelacionWebFilter.CABECERA));
        return rechazar(exchange.getResponse(), espera.get());
    }

    /** El último valor de {@code X-Forwarded-For} (el que agrega nginx) o, sin él, la dirección remota. */
    static String direccionCliente(ServerHttpRequest peticion) {
        List<String> reenviados = peticion.getHeaders().get("X-Forwarded-For");
        if (reenviados != null && !reenviados.isEmpty()) {
            String ultimaCabecera = reenviados.get(reenviados.size() - 1);
            String[] partes = ultimaCabecera.split(",");
            String ultima = partes[partes.length - 1].trim();
            if (!ultima.isEmpty()) {
                return ultima;
            }
        }
        InetSocketAddress remota = peticion.getRemoteAddress();
        if (remota == null) {
            return "desconocida";
        }
        return remota.getAddress() != null ? remota.getAddress().getHostAddress() : remota.getHostString();
    }

    private Mono<Void> rechazar(ServerHttpResponse respuesta, Duration espera) {
        long segundos = Math.max(1, (espera.toMillis() + 999) / 1000);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Demasiadas solicitudes desde esta conexión; inténtalo de nuevo en unos segundos");
        problema.setProperty("codigo", CODIGO);
        byte[] cuerpo;
        try {
            cuerpo = json.writeValueAsBytes(problema);
        } catch (JsonProcessingException e) {
            cuerpo = ("{\"status\":429,\"codigo\":\"" + CODIGO + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        respuesta.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        respuesta.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        respuesta.getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(segundos));
        DataBuffer buffer = respuesta.bufferFactory().wrap(cuerpo);
        return respuesta.writeWith(Mono.just(buffer));
    }
}
