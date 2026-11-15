package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.OfertaServicioUseCase;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ImagenSubida;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ModificacionServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.PaginaResultados;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Iteración I del Prestador en ms-catalog (DEC-311, DEC-313; contrato en {@code docs/sad/api/ms-catalog.md}): el
 * historial de modificaciones de los últimos 30 días (RF41) y la galería de un servicio aprobado (RF48). El prestador
 * es el {@code sub} del JWT; las rutas van bajo {@code /prestador/**}, solo para el rol Prestador.
 */
@RestController
@RequestMapping("/api/v1/catalogo/prestador")
public class OfertaServicioController {

    public record ModificacionDto(String idServicio, String nombreServicio, String tipo, String detalle,
                                  String valorAnterior, String valorNuevo, Instant fecha) {
        static ModificacionDto de(ModificacionServicio m) {
            return new ModificacionDto(m.idServicio(), m.nombreServicio(), m.tipo().name(), m.detalle(),
                    m.valorAnterior(), m.valorNuevo(), m.fecha());
        }
    }

    public record RespuestaHistorial(List<ModificacionDto> elementos, int pagina, int tamano, long totalElementos) {
        static RespuestaHistorial de(PaginaResultados<ModificacionServicio> p) {
            return new RespuestaHistorial(p.elementos().stream().map(ModificacionDto::de).toList(), p.pagina(),
                    p.tamano(), p.totalElementos());
        }
    }

    public record RespuestaGaleria(List<String> imagenes) {
    }

    private final OfertaServicioUseCase oferta;

    public OfertaServicioController(OfertaServicioUseCase oferta) {
        this.oferta = oferta;
    }

    private static UUID prestador(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    @GetMapping("/historial-modificaciones")
    public RespuestaHistorial historial(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam(required = false) String servicio,
                                        @RequestParam(defaultValue = "0") int pagina) {
        return RespuestaHistorial.de(oferta.historial(prestador(jwt),
                servicio == null || servicio.isBlank() ? null : servicio.trim(), pagina));
    }

    /** RF48: una imagen por petición, en la parte {@code imagen} (JPEG o PNG de hasta 5 MB). */
    @PostMapping(value = "/servicios/{id}/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RespuestaGaleria> agregarImagen(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                                          @RequestPart(name = "imagen", required = false)
                                                          MultipartFile imagen) {
        ImagenSubida subida;
        try {
            subida = new ImagenSubida(imagen == null ? null : imagen.getOriginalFilename(),
                    imagen == null ? null : imagen.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RespuestaGaleria(oferta.agregarImagen(prestador(jwt), id, subida)));
    }

    @DeleteMapping("/servicios/{id}/imagenes")
    public RespuestaGaleria quitarImagen(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                         @RequestParam String ruta) {
        return new RespuestaGaleria(oferta.quitarImagen(prestador(jwt), id, ruta));
    }
}
