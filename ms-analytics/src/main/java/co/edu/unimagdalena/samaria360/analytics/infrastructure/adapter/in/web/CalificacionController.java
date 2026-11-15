package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.CalificarUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.CalificarUseCase.ComandoCalificar;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.CalificarUseCase.Elegibilidad;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ConsultarCalificacionesUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ConsultarCalificacionesUseCase.CalificacionesEntidad;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Calificacion;
import co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.web.error.DatosInvalidosException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF17 (decisión 1 del equipo, DEC-300): las calificaciones visibles de un servicio o un atractivo (públicas, sin datos
 * del turista), la elegibilidad del Turista y calificar. Contrato en {@code docs/sad/api/ms-analytics.md}.
 */
@RestController
@RequestMapping("/api/v1/analytics/calificaciones")
public class CalificacionController {

    public record SolicitudCalificacion(String tipoEntidad, String idEntidad, Integer estrellas, String comentario) {
    }

    /** Pública: sin el id del turista. */
    public record CalificacionPublica(String id, int estrellas, String comentario, Instant fecha) {
        static CalificacionPublica de(Calificacion c) {
            return new CalificacionPublica(c.id(), c.estrellas(), c.comentario(), c.fecha());
        }
    }

    public record RespuestaEntidad(BigDecimal promedio, long cantidad, List<CalificacionPublica> elementos, int pagina,
                                   int tamano, long totalElementos) {
        static RespuestaEntidad de(CalificacionesEntidad c) {
            return new RespuestaEntidad(c.promedio().promedio(), c.promedio().cantidad(),
                    c.pagina().elementos().stream().map(CalificacionPublica::de).toList(), c.pagina().pagina(),
                    c.pagina().tamano(), c.pagina().totalElementos());
        }
    }

    public record RespuestaElegibilidad(boolean puedeCalificar, String motivo, CalificacionPublica propia) {
        static RespuestaElegibilidad de(Elegibilidad e) {
            return new RespuestaElegibilidad(e.puedeCalificar(), e.motivo(),
                    e.propia() == null ? null : CalificacionPublica.de(e.propia()));
        }
    }

    public record RespuestaCalificacion(String id, String tipoEntidad, String idEntidad, int estrellas,
                                        String comentario, Instant fecha) {
    }

    private final CalificarUseCase calificar;
    private final ConsultarCalificacionesUseCase consultar;

    public CalificacionController(CalificarUseCase calificar, ConsultarCalificacionesUseCase consultar) {
        this.calificar = calificar;
        this.consultar = consultar;
    }

    @GetMapping
    public RespuestaEntidad deEntidad(@RequestParam String tipo, @RequestParam String id,
                                      @RequestParam(defaultValue = "0") int pagina) {
        if (pagina < 0) {
            throw new DatosInvalidosException(Map.of("pagina", "FORMATO"));
        }
        return RespuestaEntidad.de(consultar.deEntidad(tipo, id, pagina));
    }

    @GetMapping("/elegibilidad")
    public RespuestaElegibilidad elegibilidad(@AuthenticationPrincipal Jwt jwt, @RequestParam String tipo,
                                              @RequestParam String id) {
        return RespuestaElegibilidad.de(calificar.elegibilidad(UUID.fromString(jwt.getSubject()), tipo, id));
    }

    @PostMapping
    public ResponseEntity<RespuestaCalificacion> calificar(@AuthenticationPrincipal Jwt jwt,
                                                           @RequestBody SolicitudCalificacion solicitud) {
        Calificacion c = calificar.calificar(new ComandoCalificar(UUID.fromString(jwt.getSubject()),
                solicitud.tipoEntidad(), solicitud.idEntidad(), solicitud.estrellas(), solicitud.comentario()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new RespuestaCalificacion(c.id(),
                c.tipoEntidad().name(), c.idEntidad(), c.estrellas(), c.comentario(), c.fecha()));
    }
}
