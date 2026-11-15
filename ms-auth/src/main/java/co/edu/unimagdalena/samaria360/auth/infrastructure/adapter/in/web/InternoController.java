package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.auth.application.port.in.ConsultarContactosUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.EmitirTokenServicioUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.TokenServicioEmitido;
import co.edu.unimagdalena.samaria360.auth.domain.model.Contacto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints internos de ms-auth (DEC-280; contrato en {@code docs/sad/api/ms-auth.md}). El gateway no enruta
 * {@code /internal/**}: solo los alcanzan los servicios dentro de la red de Docker.
 * <ul>
 *   <li>{@code POST /internal/v1/tokens}: credenciales de cliente → JWT de servicio ({@code aud = ms-auth}, 5 min).</li>
 *   <li>{@code POST /internal/v1/contactos}: con ese JWT, los contactos del rol que el cliente puede ver.</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1")
public class InternoController {

    public record SolicitudToken(String cliente, String secreto) {
        @Override
        public String toString() {
            return "SolicitudToken[" + cliente + ", ****]";
        }
    }

    public record RespuestaToken(String token, String tipo, Instant expiraEn) {
    }

    public record SolicitudContactos(List<UUID> ids) {
    }

    public record ContactoDto(UUID idUsuario, String rol, String nombre, String correo, String telefono) {
        static ContactoDto de(Contacto contacto) {
            return new ContactoDto(contacto.idUsuario(), contacto.rol().name(), contacto.nombre(), contacto.correo(),
                    contacto.telefono());
        }
    }

    public record RespuestaContactos(List<ContactoDto> elementos) {
    }

    private final EmitirTokenServicioUseCase tokens;
    private final ConsultarContactosUseCase contactos;

    public InternoController(EmitirTokenServicioUseCase tokens, ConsultarContactosUseCase contactos) {
        this.tokens = tokens;
        this.contactos = contactos;
    }

    @PostMapping("/tokens")
    public RespuestaToken token(@RequestBody SolicitudToken solicitud) {
        TokenServicioEmitido emitido = tokens.emitir(solicitud.cliente(), solicitud.secreto());
        return new RespuestaToken(emitido.token(), "Bearer", emitido.expiraEn());
    }

    @PostMapping("/contactos")
    public RespuestaContactos contactos(@AuthenticationPrincipal Jwt jwt, @RequestBody SolicitudContactos solicitud) {
        return new RespuestaContactos(contactos.consultar(jwt.getSubject(), solicitud.ids()).stream()
                .map(ContactoDto::de).toList());
    }
}
