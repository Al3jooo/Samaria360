package co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.chatbot.application.port.in.GestionarItinerariosUseCase;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Itinerario;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** RF35 (contrato en {@code docs/sad/api/ms-chatbot.md}): itinerarios del Turista autenticado. */
@RestController
@RequestMapping("/api/v1/chatbot/itinerarios")
public class ItinerarioController {

    public record SolicitudItinerario(String nombre, List<Itinerario.Item> items) {
    }

    public record ItinerarioDto(String id, String nombre, List<Itinerario.Item> items, Instant fechaCreacion,
                                Instant fechaActualizacion) {
        static ItinerarioDto de(Itinerario i) {
            return new ItinerarioDto(i.id(), i.nombre(), i.items(), i.fechaCreacion(), i.fechaActualizacion());
        }
    }

    public record RespuestaLista(List<ItinerarioDto> elementos) {
    }

    private final GestionarItinerariosUseCase itinerarios;

    public ItinerarioController(GestionarItinerariosUseCase itinerarios) {
        this.itinerarios = itinerarios;
    }

    @GetMapping
    public RespuestaLista listar(@AuthenticationPrincipal Jwt jwt) {
        return new RespuestaLista(itinerarios.listar(turista(jwt)).stream().map(ItinerarioDto::de).toList());
    }

    @GetMapping("/{id}")
    public ItinerarioDto obtener(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return ItinerarioDto.de(itinerarios.obtener(turista(jwt), id));
    }

    @PostMapping
    public ResponseEntity<ItinerarioDto> guardar(@AuthenticationPrincipal Jwt jwt,
                                                 @RequestBody SolicitudItinerario solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ItinerarioDto.de(
                itinerarios.guardar(turista(jwt), solicitud.nombre(), solicitud.items())));
    }

    @PutMapping("/{id}")
    public ItinerarioDto actualizar(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                    @RequestBody SolicitudItinerario solicitud) {
        return ItinerarioDto.de(itinerarios.actualizar(turista(jwt), id, solicitud.nombre(), solicitud.items()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        itinerarios.eliminar(turista(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static UUID turista(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
