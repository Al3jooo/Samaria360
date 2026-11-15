package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.error;

import co.edu.unimagdalena.samaria360.catalog.domain.exception.AtractivoModificadoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.AtractivoNoEncontradoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.AvisoNoEncontradoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.AvisoYaInactivoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ContenidoInvalidoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.CriteriosBusquedaInvalidosException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.EstadoServicioCambiadoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ImagenNoEncontradaException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.LimiteFavoritosException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.LimitePreguntasException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.OfertaNoDisponibleException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.PreguntaNoEncontradaException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.PrestadorNoHabilitadoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ServicioModificadoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ServicioNoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/** Errores como ProblemDetail (RFC 9457) con un {@code codigo} estable, igual que ms-auth. */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(CriteriosBusquedaInvalidosException.class)
    ProblemDetail criteriosInvalidos(CriteriosBusquedaInvalidosException ex) {
        return Problemas.crear(HttpStatus.BAD_REQUEST, CodigoError.CRITERIOS_INVALIDOS, ex.getMessage());
    }

    /** El mismo 404 para un id inválido, inexistente o no visible: no se revela cuál (RF16, RF22). */
    @ExceptionHandler(OfertaNoDisponibleException.class)
    ProblemDetail ofertaNoDisponible(OfertaNoDisponibleException ex) {
        return Problemas.crear(HttpStatus.NOT_FOUND, CodigoError.OFERTA_NO_DISPONIBLE,
                "El servicio no existe o no está disponible");
    }

    /** Cuerpo de un endpoint interno con campos inválidos: un código por campo, con el nombre JSON (nunca textos). */
    @ExceptionHandler(DatosInvalidosException.class)
    ProblemDetail datosInvalidos(DatosInvalidosException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS,
                "Datos de entrada inválidos");
        problema.setProperty("campos", ex.getCampos());
        return problema;
    }

    /** Pregunta frecuente o aviso con campos inválidos (DEC-61, DEC-58): un código por campo, con el nombre JSON. */
    @ExceptionHandler(ContenidoInvalidoException.class)
    ProblemDetail contenidoInvalido(ContenidoInvalidoException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS,
                "Datos de entrada inválidos");
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getCampos().forEach((campo, motivo) -> campos.put(campo, motivo.name()));
        problema.setProperty("campos", campos);
        return problema;
    }

    @ExceptionHandler(PreguntaNoEncontradaException.class)
    ProblemDetail preguntaNoEncontrada(PreguntaNoEncontradaException ex) {
        return Problemas.crear(HttpStatus.NOT_FOUND, CodigoError.FAQ_NO_ENCONTRADA, ex.getMessage());
    }

    @ExceptionHandler(LimitePreguntasException.class)
    ProblemDetail limitePreguntas(LimitePreguntasException ex) {
        return Problemas.crear(HttpStatus.CONFLICT, CodigoError.LIMITE_FAQ, ex.getMessage());
    }

    @ExceptionHandler(LimiteFavoritosException.class)
    ProblemDetail limiteFavoritos(LimiteFavoritosException ex) {
        return Problemas.crear(HttpStatus.CONFLICT, CodigoError.LIMITE_FAVORITOS, ex.getMessage());
    }

    @ExceptionHandler(AvisoNoEncontradoException.class)
    ProblemDetail avisoNoEncontrado(AvisoNoEncontradoException ex) {
        return Problemas.crear(HttpStatus.NOT_FOUND, CodigoError.AVISO_NO_ENCONTRADO, ex.getMessage());
    }

    /** Otro Administrador lo despublicó antes, o ya estaba despublicado (DEC-64). */
    @ExceptionHandler(AvisoYaInactivoException.class)
    ProblemDetail avisoYaInactivo(AvisoYaInactivoException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.CONFLICT, CodigoError.ESTADO_CAMBIADO, ex.getMessage());
        problema.setProperty("estadoActual", "Inactivo");
        return problema;
    }

    /** El usuario es el dueño de la cuenta: no se revela nada que no sepa (DEC-139). */
    @ExceptionHandler(PrestadorNoHabilitadoException.class)
    ProblemDetail prestadorNoHabilitado(PrestadorNoHabilitadoException ex) {
        return Problemas.crear(HttpStatus.FORBIDDEN, CodigoError.PRESTADOR_NO_HABILITADO, ex.getMessage());
    }

    @ExceptionHandler(ServicioNoEncontradoException.class)
    ProblemDetail servicioNoEncontrado(ServicioNoEncontradoException ex) {
        return Problemas.crear(HttpStatus.NOT_FOUND, CodigoError.SERVICIO_NO_ENCONTRADO, ex.getMessage());
    }

    /** Otro Administrador ya aprobó o rechazó el servicio (DEC-128): el estado que tiene ahora. */
    @ExceptionHandler(co.edu.unimagdalena.samaria360.catalog.domain.exception.GaleriaServicioException.class)
    ProblemDetail galeria(co.edu.unimagdalena.samaria360.catalog.domain.exception.GaleriaServicioException ex) {
        return Problemas.crear(HttpStatus.CONFLICT, CodigoError.valueOf(ex.getCodigo()), ex.getMessage());
    }

    @ExceptionHandler(EstadoServicioCambiadoException.class)
    ProblemDetail estadoServicioCambiado(EstadoServicioCambiadoException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.CONFLICT, CodigoError.ESTADO_CAMBIADO, ex.getMessage());
        problema.setProperty("estadoActual", ex.getEstadoActual().name());
        return problema;
    }

    /** Otra edición del Prestador o la decisión del Administrador cambiaron el servicio (DEC-186). */
    @ExceptionHandler(ServicioModificadoException.class)
    ProblemDetail servicioModificado(ServicioModificadoException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.CONFLICT, CodigoError.SERVICIO_MODIFICADO, ex.getMessage());
        problema.setProperty("versionActual", ex.getVersionActual());
        return problema;
    }

    @ExceptionHandler(AtractivoNoEncontradoException.class)
    ProblemDetail atractivoNoEncontrado(AtractivoNoEncontradoException ex) {
        return Problemas.crear(HttpStatus.NOT_FOUND, CodigoError.ATRACTIVO_NO_ENCONTRADO, ex.getMessage());
    }

    /** Otra edición o un cambio de estado del Gestor cambiaron el atractivo (DEC-207). */
    @ExceptionHandler(AtractivoModificadoException.class)
    ProblemDetail atractivoModificado(AtractivoModificadoException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.CONFLICT, CodigoError.ATRACTIVO_MODIFICADO,
                ex.getMessage());
        problema.setProperty("versionActual", ex.getVersionActual());
        return problema;
    }

    @ExceptionHandler(ImagenNoEncontradaException.class)
    ProblemDetail imagenNoEncontrada(ImagenNoEncontradaException ex) {
        return Problemas.crear(HttpStatus.NOT_FOUND, CodigoError.IMAGEN_NO_ENCONTRADA, ex.getMessage());
    }

    /** Las de Spring Security las resuelven su entry point y su access denied handler, no este advice. */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void relanzarExcepcionDeSeguridad(RuntimeException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail errorInterno(Exception ex) {
        LOG.error("Error no controlado", ex);
        return Problemas.crear(HttpStatus.INTERNAL_SERVER_ERROR, CodigoError.ERROR_INTERNO, "Ocurrió un error inesperado");
    }

    /** Un parámetro con tipo o valor inválido (p. ej. categoria=Inexistente o precioMax=abc) es un criterio inválido. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String parametro = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName()
                : ex.getPropertyName();
        ProblemDetail problema = Problemas.crear(HttpStatus.BAD_REQUEST, CodigoError.CRITERIOS_INVALIDOS,
                "El parámetro «" + parametro + "» tiene un valor inválido: " + ex.getValue());
        return handleExceptionInternal(ex, problema, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Un cuerpo que no es JSON (o que falta) es un dato inválido sin {@code campos}, como en ms-auth. El relevo de ms-auth
     * no distingue el {@code codigo} de un 4xx del endpoint interno: solo registra el estado y reintenta.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problema = Problemas.crear(status, CodigoError.DATOS_INVALIDOS,
                "El cuerpo de la solicitud no es un JSON válido");
        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    /**
     * Multipart por encima de {@code spring.servlet.multipart} (ADR-009): 413 con un código propio. Una imagen de más de
     * 5 MB que cabe en esos topes llega al dominio y recibe su motivo por campo (DEC-209).
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
                                                                          HttpHeaders headers, HttpStatusCode status,
                                                                          WebRequest request) {
        ProblemDetail problema = Problemas.crear(HttpStatus.PAYLOAD_TOO_LARGE, CodigoError.CARGA_DEMASIADO_GRANDE,
                "La carga supera el tamaño máximo permitido");
        return handleExceptionInternal(ex, problema, headers, HttpStatus.PAYLOAD_TOO_LARGE, request);
    }

    /** Agrega un código genérico a los errores estándar de Spring MVC (404, 405…). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> respuesta = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (respuesta != null && respuesta.getBody() instanceof ProblemDetail problema
                && (problema.getProperties() == null || !problema.getProperties().containsKey(Problemas.CODIGO))) {
            CodigoError codigo = statusCode.is5xxServerError() ? CodigoError.ERROR_INTERNO : CodigoError.SOLICITUD_INVALIDA;
            problema.setProperty(Problemas.CODIGO, codigo.name());
        }
        return respuesta;
    }
}
