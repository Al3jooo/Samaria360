package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.AfluenciaUseCase;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto.RespuestaAfluencia;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ActualizarAtractivoUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultarAtractivosGestorUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.RegistrarAtractivoUseCase;
import co.edu.unimagdalena.samaria360.catalog.domain.model.AtractivoPublico;
import co.edu.unimagdalena.samaria360.catalog.domain.model.BorradorAtractivo;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ImagenSubida;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto.ElementoAtractivoGestor;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto.RespuestaAtractivoGestion;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto.RespuestaPagina;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto.SolicitudAtractivo;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto.SolicitudEstadoAtractivo;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Gestión de atractivos (DEC-206): CU «Registrar atractivo» (RF06, RF07, RF24, RF27, RF33, RF58), «Actualizar
 * atractivo» con su flujo «Consultar atractivos» (WF-24) y «Cambiar estado operativo» (RF33). Solo el rol Gestor (ver
 * {@code SecurityConfig}, DEC-220). Contrato en {@code docs/sad/api/ms-catalog.md}.
 */
@RestController
@RequestMapping("/api/v1/catalogo/gestor/atractivos")
public class AtractivoGestorController {

    private static final String RUTA = "/api/v1/catalogo/gestor/atractivos/";

    private final RegistrarAtractivoUseCase registrar;
    private final ConsultarAtractivosGestorUseCase consultar;
    private final ActualizarAtractivoUseCase actualizar;
    private final AfluenciaUseCase afluencia;

    public AtractivoGestorController(RegistrarAtractivoUseCase registrar, ConsultarAtractivosGestorUseCase consultar,
                                     ActualizarAtractivoUseCase actualizar, AfluenciaUseCase afluencia) {
        this.registrar = registrar;
        this.consultar = consultar;
        this.actualizar = actualizar;
        this.afluencia = afluencia;
    }

    public record SolicitudOcupacion(Integer ocupacion) {
    }

    /** RF39 (decisión 2 del equipo, DEC-304): el Gestor registra la ocupación actual; responde el nivel calculado. */
    @PostMapping("/{id}/ocupacion")
    public RespuestaAfluencia.AfluenciaDto registrarOcupacion(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                                             @RequestBody SolicitudOcupacion solicitud) {
        return RespuestaAfluencia.AfluenciaDto.de(afluencia.registrar(id, UUID.fromString(jwt.getSubject()),
                solicitud.ocupacion()));
    }

    /**
     * Multipart (DEC-216): la parte {@code datos} (JSON) y de 1 a 5 partes {@code imagenes}. Sin {@code datos}, cada
     * campo obligatorio es un 400 con su motivo, como un JSON vacío. 201 con el atractivo publicado (DEC-87).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RespuestaAtractivoGestion> registrar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart(name = "datos", required = false) SolicitudAtractivo datos,
            @RequestPart(name = "imagenes", required = false) List<MultipartFile> imagenes) {
        BorradorAtractivo borrador = datos == null ? null : datos.aBorrador();
        AtractivoPublico atractivo = registrar.registrar(UUID.fromString(jwt.getSubject()), borrador,
                imagenes == null ? List.of() : imagenes.stream().map(AtractivoGestorController::imagen).toList());
        return ResponseEntity.created(URI.create(RUTA + atractivo.getId().orElseThrow()))
                .body(RespuestaAtractivoGestion.de(atractivo));
    }

    /** {@code pagina} llega como texto para responder igual a «abc» que a «-1»: 400 con {@code campos.pagina}. */
    @GetMapping
    public RespuestaPagina<ElementoAtractivoGestor> listar(@RequestParam(defaultValue = "0") String pagina) {
        return RespuestaPagina.de(consultar.listar(ServicioPrestadorController.numero(pagina)),
                ElementoAtractivoGestor::de);
    }

    @GetMapping("/{id}")
    public RespuestaAtractivoGestion detalle(@PathVariable String id) {
        return RespuestaAtractivoGestion.de(consultar.detalle(id));
    }

    /** 200 con el atractivo y su versión nueva; las imágenes y el estado no cambian aquí (DEC-209, DEC-210). */
    @PutMapping("/{id}")
    public RespuestaAtractivoGestion actualizar(@PathVariable String id, @RequestBody SolicitudAtractivo solicitud) {
        return RespuestaAtractivoGestion.de(actualizar.actualizar(id, solicitud.aBorrador(), solicitud.version()));
    }

    /** RF33: 200 con el atractivo y su versión nueva. */
    @PostMapping("/{id}/estado")
    public RespuestaAtractivoGestion cambiarEstado(@PathVariable String id,
                                                   @RequestBody SolicitudEstadoAtractivo solicitud) {
        return RespuestaAtractivoGestion.de(actualizar.cambiarEstado(id, solicitud.estadoNuevo(),
                solicitud.version()));
    }

    private static ImagenSubida imagen(MultipartFile archivo) {
        try {
            return new ImagenSubida(archivo.getOriginalFilename(), archivo.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
