package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.ConsultarAuditoriaUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.FiltroAuditoria;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.PaginaAuditoria;
import co.edu.unimagdalena.samaria360.analytics.domain.model.AccionAuditada;
import co.edu.unimagdalena.samaria360.analytics.domain.model.RegistroAuditoria;
import co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.web.error.DatosInvalidosException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Log de auditoría para el Administrador (RF13; contrato en {@code docs/sad/api/ms-analytics.md}): filtros opcionales
 * de acción, usuario y rango de fechas ({@code yyyy-MM-dd}, días de Santa Marta, {@code hasta} incluido), páginas de 20.
 */
@RestController
@RequestMapping("/api/v1/analytics/admin/auditoria")
public class AuditoriaController {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    public record RegistroDto(UUID usuarioId, String accion, String detalle, Instant fecha) {
        static RegistroDto de(RegistroAuditoria r) {
            return new RegistroDto(r.usuarioId(), r.accion().name(), r.detalle(), r.fecha());
        }
    }

    public record RespuestaAuditoria(List<RegistroDto> elementos, int pagina, int tamano, long totalElementos) {
        static RespuestaAuditoria de(PaginaAuditoria p) {
            return new RespuestaAuditoria(p.elementos().stream().map(RegistroDto::de).toList(), p.pagina(), p.tamano(),
                    p.totalElementos());
        }
    }

    private final ConsultarAuditoriaUseCase auditoria;

    public AuditoriaController(ConsultarAuditoriaUseCase auditoria) {
        this.auditoria = auditoria;
    }

    @GetMapping
    public RespuestaAuditoria listar(@RequestParam(required = false) String accion,
                                     @RequestParam(required = false) String usuario,
                                     @RequestParam(required = false) String desde,
                                     @RequestParam(required = false) String hasta,
                                     @RequestParam(defaultValue = "0") int pagina) {
        Map<String, String> campos = new LinkedHashMap<>();
        AccionAuditada accionFiltro = null;
        if (accion != null && !accion.isBlank()) {
            try {
                accionFiltro = AccionAuditada.desdeTexto(accion);
            } catch (IllegalArgumentException e) {
                campos.put("accion", "NO_PERMITIDO");
            }
        }
        UUID usuarioFiltro = null;
        if (usuario != null && !usuario.isBlank()) {
            try {
                usuarioFiltro = UUID.fromString(usuario.trim());
            } catch (IllegalArgumentException e) {
                campos.put("usuario", "FORMATO");
            }
        }
        Instant inicio = dia(desde, "desde", campos, false);
        Instant fin = dia(hasta, "hasta", campos, true);
        if (inicio != null && fin != null && !inicio.isBefore(fin)) {
            campos.put("hasta", "NO_PERMITIDO");
        }
        if (pagina < 0) {
            campos.put("pagina", "FORMATO");
        }
        if (!campos.isEmpty()) {
            throw new DatosInvalidosException(campos);
        }
        return RespuestaAuditoria.de(auditoria.listar(new FiltroAuditoria(accionFiltro, usuarioFiltro, inicio, fin),
                pagina));
    }

    /** {@code desde}: 00:00 del día; {@code hasta}: 00:00 del día siguiente (incluido), en Santa Marta. */
    private static Instant dia(String texto, String campo, Map<String, String> campos, boolean finDelDia) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            LocalDate fecha = LocalDate.parse(texto.trim());
            return (finDelDia ? fecha.plusDays(1) : fecha).atStartOfDay(ZONA).toInstant();
        } catch (DateTimeParseException e) {
            campos.put(campo, "FORMATO");
            return null;
        }
    }
}
