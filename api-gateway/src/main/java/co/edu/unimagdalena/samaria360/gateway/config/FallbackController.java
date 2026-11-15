package co.edu.unimagdalena.samaria360.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.util.Map;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR;

/**
 * Respuesta cuando un servicio está caído, lento o con el circuito abierto: ProblemDetail 503 con el mismo formato
 * de errores que ms-auth, ms-catalog y ms-booking ({@code codigo} estable para el frontend).
 */
@RestController
public class FallbackController {

    private static final Logger LOG = LoggerFactory.getLogger(FallbackController.class);
    private static final Map<String, String> SERVICIOS = Map.of("auth", "ms-auth", "catalogo", "ms-catalog",
            "reservas", "ms-booking", "analitica", "ms-analytics", "asistente", "ms-chatbot");

    @RequestMapping("/fallback/{ruta}")
    public ResponseEntity<ProblemDetail> servicioNoDisponible(@PathVariable String ruta, ServerWebExchange exchange) {
        String servicio = SERVICIOS.getOrDefault(ruta, ruta);
        Throwable causa = exchange.getAttribute(CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        LOG.warn("Servicio no disponible: {} (causa: {}) correlacion={}", servicio,
                causa == null ? "—" : causa.getClass().getSimpleName() + ": " + causa.getMessage(),
                exchange.getRequest().getHeaders().getFirst(CorrelacionWebFilter.CABECERA));

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio " + servicio + " no está disponible en este momento");
        problema.setInstance(rutaOriginal(exchange));
        problema.setProperty("codigo", "SERVICIO_NO_DISPONIBLE");
        problema.setProperty("servicio", servicio);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema);
    }

    /** La ruta pedida por el cliente, no la del forward interno al fallback (la guarda CorrelacionWebFilter). */
    private static URI rutaOriginal(ServerWebExchange exchange) {
        String ruta = exchange.getAttribute(CorrelacionWebFilter.ATRIBUTO_RUTA_ORIGINAL);
        return URI.create(ruta != null ? ruta : exchange.getRequest().getPath().value());
    }
}
