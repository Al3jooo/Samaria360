package co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.chatbot.application.port.in.ConversarUseCase;
import co.edu.unimagdalena.samaria360.chatbot.application.port.in.ConversarUseCase.ComandoMensaje;
import co.edu.unimagdalena.samaria360.chatbot.application.port.in.ConversarUseCase.RespuestaAsistente;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Itinerario;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Sugerencia;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * RF04, RF36 y RF59 (contrato en {@code docs/sad/api/ms-chatbot.md}): un mensaje del Turista y la respuesta del
 * asistente con sus sugerencias justificadas y, si la pide, el itinerario. Sin historial en el servidor: la conversación
 * vive en el navegador.
 */
@RestController
@RequestMapping("/api/v1/chatbot/mensajes")
public class AsistenteController {

    public record SolicitudMensaje(String mensaje, String idioma, List<String> intereses) {
    }

    public record SugerenciaDto(String tipo, String id, String nombre, String categoria, String criterio,
                                String justificacion) {
        static SugerenciaDto de(Sugerencia s) {
            return new SugerenciaDto(s.item().tipo(), s.item().id(), s.item().nombre(), s.item().categoria(),
                    s.criterio().name(), s.justificacion());
        }
    }

    public record RespuestaDto(String respuesta, List<SugerenciaDto> sugerencias, List<Itinerario.Item> itinerario,
                               List<String> fuentes, boolean catalogoDisponible, int mensajesRestantes) {
        static RespuestaDto de(RespuestaAsistente r) {
            return new RespuestaDto(r.texto(), r.sugerencias().stream().map(SugerenciaDto::de).toList(), r.itinerario(),
                    r.fuentes(), r.catalogoDisponible(), r.mensajesRestantes());
        }
    }

    private final ConversarUseCase conversar;

    public AsistenteController(ConversarUseCase conversar) {
        this.conversar = conversar;
    }

    @PostMapping
    public RespuestaDto enviar(@AuthenticationPrincipal Jwt jwt, @RequestBody SolicitudMensaje solicitud) {
        return RespuestaDto.de(conversar.conversar(new ComandoMensaje(UUID.fromString(jwt.getSubject()),
                solicitud.mensaje(), solicitud.idioma(), solicitud.intereses())));
    }
}
