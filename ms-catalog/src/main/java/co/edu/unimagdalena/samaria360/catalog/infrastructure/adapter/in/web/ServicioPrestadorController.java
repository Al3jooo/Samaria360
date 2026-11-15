package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.ActualizarServicioUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultarMisServiciosUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.RegistrarServicioUseCase;
import co.edu.unimagdalena.samaria360.catalog.domain.model.BorradorServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ImagenSubida;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioComercial;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto.ElementoServicioPrestador;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto.RespuestaPagina;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto.RespuestaServicioGestion;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto.SolicitudDatosComerciales;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto.SolicitudPrecio;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto.SolicitudServicio;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.error.DatosInvalidosException;
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
import java.util.Map;
import java.util.UUID;

/**
 * CU «Registrar servicio comercial» (RF09, RF10, RF37) y su flujo «Consultar mis servicios» (DEC-129, DEC-143), y la
 * edición del Prestador: CU «Actualizar tarifas» (RF32) y la parte del servicio de «Actualizar datos comerciales» (RF43).
 * Solo el rol Prestador (ver {@code SecurityConfig}); el prestador es siempre el del claim {@code sub}. Contrato en
 * {@code docs/sad/api/ms-catalog.md}.
 */
@RestController
@RequestMapping("/api/v1/catalogo/prestador/servicios")
public class ServicioPrestadorController {

    private static final String RUTA = "/api/v1/catalogo/prestador/servicios/";

    private final RegistrarServicioUseCase registrar;
    private final ConsultarMisServiciosUseCase consultar;
    private final ActualizarServicioUseCase actualizar;

    public ServicioPrestadorController(RegistrarServicioUseCase registrar, ConsultarMisServiciosUseCase consultar,
                                       ActualizarServicioUseCase actualizar) {
        this.registrar = registrar;
        this.consultar = consultar;
        this.actualizar = actualizar;
    }

    /** 201 con el servicio Pendiente de aprobación (DEC-124). JSON, sin fotos (el contrato anterior a DEC-318). */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RespuestaServicioGestion> registrar(@AuthenticationPrincipal Jwt jwt,
                                                              @RequestBody SolicitudServicio solicitud) {
        return creado(registrar.registrar(prestador(jwt), solicitud.aBorrador()));
    }

    /**
     * Registro con fotos (WF-08, DEC-318), multipart como el de los atractivos (DEC-216): la parte {@code datos} (el
     * mismo JSON) y de 1 a 5 partes {@code imagenes}. Sin {@code datos}, cada campo obligatorio es un 400.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RespuestaServicioGestion> registrarConImagenes(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart(name = "datos", required = false) SolicitudServicio datos,
            @RequestPart(name = "imagenes", required = false) List<MultipartFile> imagenes) {
        BorradorServicio borrador = datos == null ? null : datos.aBorrador();
        return creado(registrar.registrar(prestador(jwt), borrador,
                imagenes == null ? List.of() : imagenes.stream().map(ServicioPrestadorController::imagen).toList()));
    }

    private static ResponseEntity<RespuestaServicioGestion> creado(ServicioComercial servicio) {
        return ResponseEntity.created(URI.create(RUTA + servicio.getId().orElseThrow()))
                .body(RespuestaServicioGestion.paraPrestador(servicio));
    }

    private static ImagenSubida imagen(MultipartFile archivo) {
        try {
            return new ImagenSubida(archivo.getOriginalFilename(), archivo.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code pagina} llega como texto para responder igual a «abc» que a «-1»: 400 con {@code campos.pagina}. */
    @GetMapping
    public RespuestaPagina<ElementoServicioPrestador> listar(@AuthenticationPrincipal Jwt jwt,
                                                             @RequestParam(required = false) String estado,
                                                             @RequestParam(defaultValue = "0") String pagina) {
        return RespuestaPagina.de(consultar.listar(prestador(jwt), estado, numero(pagina)),
                ElementoServicioPrestador::de);
    }

    @GetMapping("/{id}")
    public RespuestaServicioGestion detalle(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
        return RespuestaServicioGestion.paraPrestador(consultar.detalle(prestador(jwt), id));
    }

    /** RF32 (DEC-184): 200 con el servicio y su versión nueva; la oferta no cambia (DEC-183). */
    @PutMapping("/{id}/precio")
    public RespuestaServicioGestion actualizarPrecio(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                                     @RequestBody SolicitudPrecio solicitud) {
        return RespuestaServicioGestion.paraPrestador(actualizar.actualizarPrecio(prestador(jwt), id,
                solicitud.precioBase(), solicitud.preciosUnidad(), solicitud.version()));
    }

    /** RF43 (DEC-188, DEC-189): 200 con el servicio y su versión nueva. */
    @PutMapping("/{id}/datos-comerciales")
    public RespuestaServicioGestion actualizarDatosComerciales(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable String id,
                                                               @RequestBody SolicitudDatosComerciales solicitud) {
        return RespuestaServicioGestion.paraPrestador(actualizar.actualizarDatosComerciales(prestador(jwt), id,
                solicitud.nombreComercial(), solicitud.datosContacto(), solicitud.version()));
    }

    private static UUID prestador(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    static int numero(String pagina) {
        try {
            return Integer.parseInt(pagina.strip());
        } catch (NumberFormatException e) {
            throw new DatosInvalidosException(Map.of("pagina", "FORMATO"));
        }
    }
}
