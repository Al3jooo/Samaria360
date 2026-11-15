package co.edu.unimagdalena.samaria360.booking.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.booking.application.port.in.ConsultarAsistentesUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.in.ConsultarAsistentesUseCase.ListadoAsistentes;
import co.edu.unimagdalena.samaria360.booking.application.port.in.GestionarOfertaPrestadorUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.in.GestionarOfertaPrestadorUseCase.ComandoFila;
import co.edu.unimagdalena.samaria360.booking.application.port.in.GestionarOfertaPrestadorUseCase.ComandoPlantilla;
import co.edu.unimagdalena.samaria360.booking.application.port.in.GestionarOfertaPrestadorUseCase.Inventario;
import co.edu.unimagdalena.samaria360.booking.domain.exception.DatosReservaInvalidosException;
import co.edu.unimagdalena.samaria360.booking.domain.model.FilaPrestador;
import co.edu.unimagdalena.samaria360.booking.domain.model.MotivoInvalido;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF19 y RF42 del Prestador (DEC-310, DEC-312; contrato en {@code docs/sad/api/ms-booking.md}): el inventario de su
 * servicio con sus cambios y la plantilla, y el listado de asistentes. El prestador es el {@code sub} del JWT.
 */
@RestController
@RequestMapping("/api/v1/reservas/prestador")
public class OfertaPrestadorController {

    public record SolicitudFila(String fecha, String horaInicio, String idUnidad, Integer cuposTotales) {
    }

    public record SolicitudPlantilla(List<String> diasOperacion, List<String> salidas) {
    }

    public record FilaDto(String categoria, String idUnidad, LocalDate fecha, LocalTime horaInicio, int cuposTotales,
                          int cuposDisponibles, int reservados, boolean cerrado) {
        static FilaDto de(FilaPrestador f) {
            return new FilaDto(f.categoria().name(), f.idUnidad(), f.fecha(), f.horaInicio(), f.cuposTotales(),
                    f.cuposDisponibles(), f.reservados(), f.cerrado());
        }
    }

    public record RespuestaInventario(String idServicio, String nombre, String categoria, LocalDate desde,
                                      LocalDate hasta, List<String> diasOperacion, List<String> salidas,
                                      List<FilaDto> filas) {
        static RespuestaInventario de(Inventario i) {
            return new RespuestaInventario(i.idServicio(), i.nombre(), i.categoria(), i.desde(), i.hasta(),
                    i.diasOperacion(), i.salidas(), i.filas().stream().map(FilaDto::de).toList());
        }
    }

    public record AsistenteDto(UUID idReserva, String nombre, String correo, String telefono, int personas,
                               LocalTime horaInicio, String nombreUnidad, LocalDate fechaEntrada,
                               LocalDate fechaSalida) {
    }

    public record RespuestaAsistentes(String idServicio, LocalDate fecha, LocalTime horaInicio,
                                      List<AsistenteDto> asistentes, int totalPersonas) {
        static RespuestaAsistentes de(ListadoAsistentes l) {
            return new RespuestaAsistentes(l.idServicio(), l.fecha(), l.horaInicio(), l.asistentes().stream()
                    .map(a -> new AsistenteDto(a.idReserva(), a.nombre(), a.correo(), a.telefono(), a.personas(),
                            a.horaInicio(), a.nombreUnidad(), a.fechaEntrada(), a.fechaSalida())).toList(),
                    l.totalPersonas());
        }
    }

    private final GestionarOfertaPrestadorUseCase oferta;
    private final ConsultarAsistentesUseCase asistentes;

    public OfertaPrestadorController(GestionarOfertaPrestadorUseCase oferta, ConsultarAsistentesUseCase asistentes) {
        this.oferta = oferta;
        this.asistentes = asistentes;
    }

    private static UUID prestador(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    @GetMapping("/servicios/{id}/inventario")
    public RespuestaInventario inventario(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                          @RequestParam(required = false) String desde,
                                          @RequestParam(required = false) String hasta) {
        return RespuestaInventario.de(oferta.inventario(prestador(jwt), id, dia(desde, "desde"), dia(hasta, "hasta")));
    }

    @PutMapping("/servicios/{id}/inventario")
    public FilaDto ajustar(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                           @RequestBody SolicitudFila solicitud) {
        return FilaDto.de(oferta.ajustar(new ComandoFila(prestador(jwt), id, dia(solicitud.fecha(), "fecha"),
                hora(solicitud.horaInicio()), vacioANulo(solicitud.idUnidad()), solicitud.cuposTotales())));
    }

    @DeleteMapping("/servicios/{id}/inventario")
    public FilaDto cerrar(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @RequestParam String fecha,
                          @RequestParam(required = false) String horaInicio,
                          @RequestParam(required = false) String idUnidad) {
        return FilaDto.de(oferta.cerrar(new ComandoFila(prestador(jwt), id, dia(fecha, "fecha"), hora(horaInicio),
                vacioANulo(idUnidad), null)));
    }

    @PutMapping("/servicios/{id}/plantilla")
    public ResponseEntity<Void> plantilla(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                          @RequestBody SolicitudPlantilla solicitud) {
        oferta.cambiarPlantilla(new ComandoPlantilla(prestador(jwt), id, solicitud.diasOperacion(),
                solicitud.salidas()));
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/asistentes")
    public RespuestaAsistentes asistentes(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam(required = false) String servicio,
                                          @RequestParam(required = false) String fecha,
                                          @RequestParam(required = false) String horaInicio) {
        return RespuestaAsistentes.de(asistentes.asistentes(prestador(jwt), servicio, dia(fecha, "fecha"),
                hora(horaInicio)));
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static LocalDate dia(String texto, String campo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new DatosReservaInvalidosException(Map.of(campo, MotivoInvalido.FORMATO));
        }
    }

    private static LocalTime hora(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        if (!texto.trim().matches("^[0-2][0-9]:[0-5][0-9]$")) {
            throw new DatosReservaInvalidosException(Map.of("horaInicio", MotivoInvalido.FORMATO));
        }
        try {
            return LocalTime.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new DatosReservaInvalidosException(Map.of("horaInicio", MotivoInvalido.FORMATO));
        }
    }
}
