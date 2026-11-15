package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.ReportarUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ReportarUseCase.ComandoReportar;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Reporte;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** RF57 (DEC-301): el Turista reporta información falsa o desactualizada de un servicio o un atractivo. */
@RestController
@RequestMapping("/api/v1/analytics/reportes")
public class ReporteController {

    public record SolicitudReporte(String tipoEntidad, String idEntidad, String motivo, String descripcion) {
    }

    public record RespuestaReporte(String id, String estado, Instant fecha) {
    }

    private final ReportarUseCase reportar;

    public ReporteController(ReportarUseCase reportar) {
        this.reportar = reportar;
    }

    @PostMapping
    public ResponseEntity<RespuestaReporte> reportar(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestBody SolicitudReporte solicitud) {
        Reporte r = reportar.reportar(new ComandoReportar(UUID.fromString(jwt.getSubject()), solicitud.tipoEntidad(),
                solicitud.idEntidad(), solicitud.motivo(), solicitud.descripcion()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new RespuestaReporte(r.id(), r.estado().name(), r.fecha()));
    }
}
