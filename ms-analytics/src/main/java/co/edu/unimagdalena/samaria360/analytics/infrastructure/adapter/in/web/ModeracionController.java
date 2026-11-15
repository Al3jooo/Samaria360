package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.ModerarCalificacionesUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.Pagina;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ReportarUseCase;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Calificacion;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Reporte;
import co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.web.error.DatosInvalidosException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Panel de moderación del Administrador (DEC-300, DEC-301): RF29 (ocultar, volver a mostrar o eliminar una
 * calificación con su motivo) y RF57 (los reportes, que solo se marcan revisados). Páginas de 20.
 */
@RestController
@RequestMapping("/api/v1/analytics/admin")
public class ModeracionController {

    public record SolicitudModeracion(String motivo) {
    }

    public record CalificacionModeracion(String id, String tipoEntidad, String idEntidad, String nombreEntidad,
                                         UUID turistaId, int estrellas, String comentario, String estado,
                                         String motivoModeracion, Instant fecha, Instant fechaModeracion) {
        static CalificacionModeracion de(Calificacion c) {
            return new CalificacionModeracion(c.id(), c.tipoEntidad().name(), c.idEntidad(), c.nombreEntidad(),
                    c.turistaId(), c.estrellas(), c.comentario(), c.estado().name(), c.motivoModeracion(), c.fecha(),
                    c.fechaModeracion());
        }
    }

    public record ReporteDto(String id, String tipoEntidad, String idEntidad, UUID turistaId, String motivo,
                             String descripcion, String estado, Instant fecha, UUID revisadoPor,
                             Instant fechaRevision) {
        static ReporteDto de(Reporte r) {
            return new ReporteDto(r.id(), r.tipoEntidad().name(), r.idEntidad(), r.turistaId(), r.motivo().name(),
                    r.descripcion(), r.estado().name(), r.fecha(), r.revisadoPor(), r.fechaRevision());
        }
    }

    public record RespuestaPagina<T>(List<T> elementos, int pagina, int tamano, long totalElementos) {
    }

    private final ModerarCalificacionesUseCase moderar;
    private final ReportarUseCase reportes;

    public ModeracionController(ModerarCalificacionesUseCase moderar, ReportarUseCase reportes) {
        this.moderar = moderar;
        this.reportes = reportes;
    }

    @GetMapping("/calificaciones")
    public RespuestaPagina<CalificacionModeracion> calificaciones(@RequestParam(required = false) String estado,
                                                                 @RequestParam(defaultValue = "0") int pagina) {
        validarPagina(pagina);
        Pagina<Calificacion> p = moderar.listar(estado, pagina);
        return new RespuestaPagina<>(p.elementos().stream().map(CalificacionModeracion::de).toList(), p.pagina(),
                p.tamano(), p.totalElementos());
    }

    @PostMapping("/calificaciones/{id}/ocultamiento")
    public CalificacionModeracion ocultar(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                          @RequestBody SolicitudModeracion solicitud) {
        return CalificacionModeracion.de(moderar.ocultar(id, UUID.fromString(jwt.getSubject()), solicitud.motivo()));
    }

    @PostMapping("/calificaciones/{id}/visibilidad")
    public CalificacionModeracion mostrar(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                          @RequestBody SolicitudModeracion solicitud) {
        return CalificacionModeracion.de(moderar.mostrar(id, UUID.fromString(jwt.getSubject()), solicitud.motivo()));
    }

    @PostMapping("/calificaciones/{id}/eliminacion")
    public ResponseEntity<Void> eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                         @RequestBody SolicitudModeracion solicitud) {
        moderar.eliminar(id, UUID.fromString(jwt.getSubject()), solicitud.motivo());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reportes")
    public RespuestaPagina<ReporteDto> reportes(@RequestParam(required = false) String estado,
                                                @RequestParam(defaultValue = "0") int pagina) {
        validarPagina(pagina);
        Pagina<Reporte> p = reportes.listar(estado, pagina);
        return new RespuestaPagina<>(p.elementos().stream().map(ReporteDto::de).toList(), p.pagina(), p.tamano(),
                p.totalElementos());
    }

    @PostMapping("/reportes/{id}/revision")
    public ReporteDto revisar(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return ReporteDto.de(reportes.marcarRevisado(id, UUID.fromString(jwt.getSubject())));
    }

    private static void validarPagina(int pagina) {
        if (pagina < 0) {
            throw new DatosInvalidosException(Map.of("pagina", "FORMATO"));
        }
    }
}
