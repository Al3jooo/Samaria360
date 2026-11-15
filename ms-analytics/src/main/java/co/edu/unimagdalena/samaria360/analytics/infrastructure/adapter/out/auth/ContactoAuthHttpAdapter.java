package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.out.auth;

import co.edu.unimagdalena.samaria360.analytics.application.port.out.ContactoPrestadorPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.ServicioExternoNoDisponibleException;
import co.edu.unimagdalena.samaria360.analytics.domain.model.ContactoPrestador;
import co.edu.unimagdalena.samaria360.analytics.infrastructure.config.AuthPropiedades;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Contacto del Prestador en ms-auth (DEC-280): pide un JWT de servicio con las credenciales de cliente de ms-analytics
 * ({@code POST /internal/v1/tokens}, guardado hasta 30 s antes de vencer) y consulta {@code POST /internal/v1/contactos}.
 * ms-auth solo devuelve prestadores a este cliente. El contacto no se guarda ni va a los logs.
 */
@Component
public class ContactoAuthHttpAdapter implements ContactoPrestadorPort {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaToken(String token, Instant expiraEn) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContactoDto(UUID idUsuario, String nombre, String correo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaContactos(List<ContactoDto> elementos) {
    }

    private final RestClient cliente;
    private final AuthPropiedades propiedades;
    private final RelojPort reloj;
    private RespuestaToken token;

    public ContactoAuthHttpAdapter(AuthPropiedades propiedades, RelojPort reloj) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(1));
        fabrica.setReadTimeout(Duration.ofSeconds(3));
        this.cliente = RestClient.builder().baseUrl(propiedades.url()).requestFactory(fabrica).build();
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Override
    public Optional<ContactoPrestador> buscar(UUID idPrestador) {
        try {
            RespuestaContactos respuesta = cliente.post().uri("/internal/v1/contactos")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVigente())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("ids", List.of(idPrestador)))
                    .retrieve().body(RespuestaContactos.class);
            if (respuesta == null || respuesta.elementos() == null) {
                return Optional.empty();
            }
            return respuesta.elementos().stream().filter(c -> idPrestador.equals(c.idUsuario())).findFirst()
                    .map(c -> new ContactoPrestador(c.nombre(), c.correo()));
        } catch (RestClientException error) {
            token = null;
            throw new ServicioExternoNoDisponibleException("ms-auth no respondió: " + error.getClass().getSimpleName(),
                    error);
        }
    }

    private synchronized String tokenVigente() {
        if (token == null || token.expiraEn() == null || reloj.ahora().plusSeconds(30).isAfter(token.expiraEn())) {
            token = cliente.post().uri("/internal/v1/tokens").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cliente", "ms-analytics", "secreto", propiedades.secreto()))
                    .retrieve().body(RespuestaToken.class);
        }
        return token.token();
    }
}
