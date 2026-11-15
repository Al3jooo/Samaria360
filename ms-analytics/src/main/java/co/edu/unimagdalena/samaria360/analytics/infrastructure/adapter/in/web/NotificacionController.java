package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.GestionarNotificacionesUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.PaginaNotificaciones;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Notificacion;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Campana de RF63 (contrato en {@code docs/sad/api/ms-analytics.md}): las notificaciones del usuario autenticado
 * (Turista o Prestador), de la más reciente a la más antigua, y su lectura. El usuario es siempre el {@code sub}.
 */
@RestController
@RequestMapping("/api/v1/analytics/notificaciones")
public class NotificacionController {

    public record NotificacionDto(String id, String tipo, String mensaje, Map<String, String> datos, boolean leida,
                                  Instant fecha) {
        static NotificacionDto de(Notificacion n) {
            return new NotificacionDto(n.id(), n.tipo().name(), n.mensaje(), n.datos(), n.leida(), n.fecha());
        }
    }

    public record RespuestaNotificaciones(List<NotificacionDto> elementos, int pagina, int tamano, long totalElementos,
                                          long noLeidas) {
        static RespuestaNotificaciones de(PaginaNotificaciones p) {
            return new RespuestaNotificaciones(p.elementos().stream().map(NotificacionDto::de).toList(), p.pagina(),
                    p.tamano(), p.totalElementos(), p.noLeidas());
        }
    }

    public record RespuestaLectura(long marcadas) {
    }

    private final GestionarNotificacionesUseCase notificaciones;

    public NotificacionController(GestionarNotificacionesUseCase notificaciones) {
        this.notificaciones = notificaciones;
    }

    @GetMapping
    public RespuestaNotificaciones listar(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam(defaultValue = "0") int pagina) {
        return RespuestaNotificaciones.de(notificaciones.listar(UUID.fromString(jwt.getSubject()), pagina));
    }

    @PostMapping("/{id}/lectura")
    public ResponseEntity<Void> marcarLeida(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        notificaciones.marcarLeida(UUID.fromString(jwt.getSubject()), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lectura")
    public RespuestaLectura marcarTodas(@AuthenticationPrincipal Jwt jwt) {
        return new RespuestaLectura(notificaciones.marcarTodas(UUID.fromString(jwt.getSubject())));
    }
}
