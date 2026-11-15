package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.ConsultarCalificacionesUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ConsultarCalificacionesUseCase.CalificacionesPrestador;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.FiltroCalificacionesPrestador;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ResumenServicio;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Calificacion;
import co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.web.error.DatosInvalidosException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF56 (DEC-300): las calificaciones visibles de los servicios del Prestador del token, con el promedio y filtros por
 * servicio y rango de fechas ({@code yyyy-MM-dd}, días de Santa Marta, {@code hasta} incluido), de 20 en 20. Sin datos
 * del turista.
 */
@RestController
@RequestMapping("/api/v1/analytics/prestador/calificaciones")
public class CalificacionPrestadorController {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    public record CalificacionRecibida(String id, String idServicio, String nombreServicio, int estrellas,
                                       String comentario, Instant fecha) {
        static CalificacionRecibida de(Calificacion c) {
            return new CalificacionRecibida(c.id(), c.idEntidad(), c.nombreEntidad(), c.estrellas(), c.comentario(),
                    c.fecha());
        }
    }

    public record RespuestaPrestador(BigDecimal promedio, long cantidad, List<ResumenServicio> servicios,
                                     List<CalificacionRecibida> elementos, int pagina, int tamano,
                                     long totalElementos) {
        static RespuestaPrestador de(CalificacionesPrestador c) {
            return new RespuestaPrestador(c.promedio().promedio(), c.promedio().cantidad(), c.servicios(),
                    c.pagina().elementos().stream().map(CalificacionRecibida::de).toList(), c.pagina().pagina(),
                    c.pagina().tamano(), c.pagina().totalElementos());
        }
    }

    private final ConsultarCalificacionesUseCase consultar;

    public CalificacionPrestadorController(ConsultarCalificacionesUseCase consultar) {
        this.consultar = consultar;
    }

    @GetMapping
    public RespuestaPrestador listar(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(required = false) String servicio,
                                     @RequestParam(required = false) String desde,
                                     @RequestParam(required = false) String hasta,
                                     @RequestParam(defaultValue = "0") int pagina) {
        Map<String, String> campos = new LinkedHashMap<>();
        String idServicio = servicio == null || servicio.isBlank() ? null : servicio.trim();
        if (idServicio != null && !Calificacion.esObjectId(idServicio)) {
            campos.put("servicio", "FORMATO");
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
        return RespuestaPrestador.de(consultar.delPrestador(new FiltroCalificacionesPrestador(
                UUID.fromString(jwt.getSubject()), idServicio, inicio, fin), pagina));
    }

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
