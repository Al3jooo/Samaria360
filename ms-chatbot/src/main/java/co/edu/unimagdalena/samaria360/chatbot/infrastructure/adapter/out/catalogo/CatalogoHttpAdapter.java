package co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.out.catalogo;

import co.edu.unimagdalena.samaria360.chatbot.application.port.out.CatalogoNoDisponibleException;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.CatalogoPort;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.ItemCatalogo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * Búsqueda pública de ms-catalog (DEC-280: conexión interna nueva del nivel 2, sin token ni endpoints nuevos), con los
 * mismos tiempos de espera que ms-booking (ADR-007): 500 ms de conexión y 1,5 s de lectura.
 */
@Component
public class CatalogoHttpAdapter implements CatalogoPort {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Elemento(String tipo, String id, String nombre, String categoria, String descripcionCorta,
                    String estadoOperativo, Double calificacion) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Pagina(List<Elemento> elementos) {
    }

    private final RestClient cliente;

    public CatalogoHttpAdapter(@Value("${samaria360.catalogo.url:http://localhost:8082}") String url) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofMillis(500));
        fabrica.setReadTimeout(Duration.ofMillis(1500));
        this.cliente = RestClient.builder().baseUrl(url).requestFactory(fabrica).build();
    }

    @Override
    public List<ItemCatalogo> buscar(String texto, String categoria, int tamano) {
        try {
            Pagina pagina = cliente.get().uri(u -> consulta(u, texto, categoria, tamano)).retrieve().body(Pagina.class);
            if (pagina == null || pagina.elementos() == null) {
                return List.of();
            }
            return pagina.elementos().stream()
                    .filter(e -> e.id() != null && e.nombre() != null && e.tipo() != null)
                    .map(e -> new ItemCatalogo(e.tipo(), e.id(), e.nombre(), e.categoria(), e.descripcionCorta(),
                            e.estadoOperativo(), e.calificacion()))
                    .toList();
        } catch (RestClientException error) {
            throw new CatalogoNoDisponibleException("ms-catalog no respondió: " + error.getClass().getSimpleName(),
                    error);
        }
    }

    private static URI consulta(UriBuilder u, String texto, String categoria, int tamano) {
        u.path("/api/v1/catalogo/oferta").queryParam("tamano", Math.max(1, Math.min(10, tamano)));
        if (texto != null && !texto.isBlank()) {
            u.queryParam("texto", texto);
        }
        if (categoria != null) {
            u.queryParam("categoria", categoria);
        }
        return u.build();
    }
}
