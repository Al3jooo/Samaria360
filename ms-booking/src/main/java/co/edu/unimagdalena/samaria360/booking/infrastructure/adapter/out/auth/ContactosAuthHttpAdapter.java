package co.edu.unimagdalena.samaria360.booking.infrastructure.adapter.out.auth;

import co.edu.unimagdalena.samaria360.booking.application.port.out.ContactosTuristasPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.booking.domain.exception.ContactosNoDisponiblesException;
import co.edu.unimagdalena.samaria360.booking.domain.model.ContactoTurista;
import co.edu.unimagdalena.samaria360.booking.infrastructure.config.AuthPropiedades;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contactos de los turistas en ms-auth (DEC-280, DEC-295, DEC-312): JWT de servicio con las credenciales de cliente de
 * ms-booking ({@code POST /internal/v1/tokens}, guardado hasta 30 s antes de vencer) y {@code POST
 * /internal/v1/contactos} en lotes de 100. ms-auth solo devuelve turistas a este cliente. Nada se guarda ni va a los
 * logs.
 */
@Component
public class ContactosAuthHttpAdapter implements ContactosTuristasPort {

    static final int LOTE = 100;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaToken(String token, Instant expiraEn) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContactoDto(UUID idUsuario, String nombre, String correo, String telefono) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaContactos(List<ContactoDto> elementos) {
    }

    private final RestClient cliente;
    private final AuthPropiedades propiedades;
    private final RelojPort reloj;
    private RespuestaToken token;

    public ContactosAuthHttpAdapter(AuthPropiedades propiedades, RelojPort reloj) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(1));
        fabrica.setReadTimeout(Duration.ofSeconds(3));
        this.cliente = RestClient.builder().baseUrl(propiedades.url()).requestFactory(fabrica).build();
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Override
    public Map<UUID, ContactoTurista> buscar(Collection<UUID> turistas) {
        Map<UUID, ContactoTurista> contactos = new LinkedHashMap<>();
        List<UUID> ids = new ArrayList<>(turistas);
        try {
            for (int i = 0; i < ids.size(); i += LOTE) {
                RespuestaContactos respuesta = cliente.post().uri("/internal/v1/contactos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenVigente())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("ids", ids.subList(i, Math.min(ids.size(), i + LOTE))))
                        .retrieve().body(RespuestaContactos.class);
                if (respuesta != null && respuesta.elementos() != null) {
                    respuesta.elementos().forEach(c -> contactos.put(c.idUsuario(),
                            new ContactoTurista(c.idUsuario(), c.nombre(), c.correo(), c.telefono())));
                }
            }
            return contactos;
        } catch (RestClientException error) {
            token = null;
            throw new ContactosNoDisponiblesException("ms-auth no respondió: " + error.getClass().getSimpleName(),
                    error);
        }
    }

    private synchronized String tokenVigente() {
        if (token == null || token.expiraEn() == null || reloj.ahora().plusSeconds(30).isAfter(token.expiraEn())) {
            token = cliente.post().uri("/internal/v1/tokens").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cliente", "ms-booking", "secreto", propiedades.secreto()))
                    .retrieve().body(RespuestaToken.class);
        }
        return token.token();
    }
}
