package co.edu.unimagdalena.samaria360.booking.infrastructure.adapter.in.web.error;

import co.edu.unimagdalena.samaria360.booking.domain.exception.CatalogoNoDisponibleException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.CancelacionFueraDePlazoException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.CupoAgotadoException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.DatosReservaInvalidosException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.FechaNoDisponibleException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.IdempotenciaConflictoException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.OfertaNoDisponibleException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.PrecioCambiadoException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.ReservaNoCancelableException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.ReservaNoEncontradaException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.UnidadNoDisponibleException;
import co.edu.unimagdalena.samaria360.booking.domain.model.Reserva;
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
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/** Errores como ProblemDetail (RFC 9457) con un {@code codigo} estable (§4 del plan), igual que ms-auth. */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    /** Un código por campo, con los nombres JSON de la API; nunca textos. */
    @ExceptionHandler(DatosReservaInvalidosException.class)
    ProblemDetail datosInvalidos(DatosReservaInvalidosException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS,
                "Datos de entrada inválidos");
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getCampos().forEach((campo, motivo) -> campos.put(campo, motivo.name()));
        problema.setProperty("campos", campos);
        return problema;
    }

    @ExceptionHandler(IdempotenciaRequeridaException.class)
    ProblemDetail idempotenciaRequerida(IdempotenciaRequeridaException ex) {
        return Problemas.crear(HttpStatus.BAD_REQUEST, CodigoError.IDEMPOTENCIA_REQUERIDA, ex.getMessage());
    }

    @ExceptionHandler(OfertaNoDisponibleException.class)
    ProblemDetail ofertaNoDisponible(OfertaNoDisponibleException ex) {
        return Problemas.crear(HttpStatus.CONFLICT, CodigoError.OFERTA_NO_DISPONIBLE, ex.getMessage());
    }

    @ExceptionHandler(UnidadNoDisponibleException.class)
    ProblemDetail unidadNoDisponible(UnidadNoDisponibleException ex) {
        return Problemas.crear(HttpStatus.CONFLICT, CodigoError.UNIDAD_NO_DISPONIBLE, ex.getMessage());
    }

    @ExceptionHandler(ReservaNoEncontradaException.class)
    ProblemDetail reservaNoEncontrada(ReservaNoEncontradaException ex) {
        return Problemas.crear(HttpStatus.NOT_FOUND, CodigoError.RESERVA_NO_ENCONTRADA, ex.getMessage());
    }

    @ExceptionHandler(FechaNoDisponibleException.class)
    ProblemDetail fechaNoDisponible(FechaNoDisponibleException ex) {
        return Problemas.crear(HttpStatus.CONFLICT, CodigoError.FECHA_NO_DISPONIBLE, ex.getMessage());
    }

    @ExceptionHandler(CupoAgotadoException.class)
    ProblemDetail cupoAgotado(CupoAgotadoException ex) {
        return Problemas.crear(HttpStatus.CONFLICT, CodigoError.CUPO_AGOTADO, ex.getMessage());
    }

    /** RF37: lleva el precio vigente para mostrarlo antes de volver a confirmar. */
    @ExceptionHandler(PrecioCambiadoException.class)
    ProblemDetail precioCambiado(PrecioCambiadoException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.CONFLICT, CodigoError.PRECIO_CAMBIADO, ex.getMessage());
        problema.setProperty("precioTotal", ex.getPrecioTotal());
        problema.setProperty("precioUnitario", ex.getPrecioUnitario());
        problema.setProperty("moneda", Reserva.MONEDA);
        return problema;
    }

    @ExceptionHandler(CancelacionFueraDePlazoException.class)
    ProblemDetail fueraDePlazo(CancelacionFueraDePlazoException ex) {
        return Problemas.crear(HttpStatus.CONFLICT, CodigoError.CANCELACION_FUERA_DE_PLAZO, ex.getMessage());
    }

    @ExceptionHandler(ReservaNoCancelableException.class)
    ProblemDetail noCancelable(ReservaNoCancelableException ex) {
        return Problemas.crear(HttpStatus.CONFLICT, CodigoError.RESERVA_NO_CANCELABLE, ex.getMessage());
    }

    @ExceptionHandler(IdempotenciaConflictoException.class)
    ProblemDetail idempotenciaConflicto(IdempotenciaConflictoException ex) {
        return Problemas.crear(HttpStatus.UNPROCESSABLE_ENTITY, CodigoError.IDEMPOTENCIA_CONFLICTO, ex.getMessage());
    }

    /** El mismo significado que el fallback del gateway: el servicio que no responde es ms-catalog. */
    @ExceptionHandler(co.edu.unimagdalena.samaria360.booking.domain.exception.CuposReservadosException.class)
    ProblemDetail cuposReservados(co.edu.unimagdalena.samaria360.booking.domain.exception.CuposReservadosException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.CONFLICT, CodigoError.CUPOS_RESERVADOS, ex.getMessage());
        problema.setProperty("reservados", ex.getReservados());
        return problema;
    }

    @ExceptionHandler(co.edu.unimagdalena.samaria360.booking.domain.exception.FechaConReservasException.class)
    ProblemDetail fechaConReservas(co.edu.unimagdalena.samaria360.booking.domain.exception.FechaConReservasException ex) {
        return Problemas.crear(HttpStatus.CONFLICT, CodigoError.FECHA_CON_RESERVAS, ex.getMessage());
    }

    /** RF42: ms-auth no respondió los contactos (DEC-312). */
    @ExceptionHandler(co.edu.unimagdalena.samaria360.booking.domain.exception.ContactosNoDisponiblesException.class)
    ProblemDetail contactosNoDisponibles(
            co.edu.unimagdalena.samaria360.booking.domain.exception.ContactosNoDisponiblesException ex) {
        LOG.warn("ms-auth no disponible: {}", ex.getMessage());
        ProblemDetail problema = Problemas.crear(HttpStatus.SERVICE_UNAVAILABLE, CodigoError.SERVICIO_NO_DISPONIBLE,
                "Los contactos no están disponibles en este momento");
        problema.setProperty("servicio", "ms-auth");
        return problema;
    }

    @ExceptionHandler(CatalogoNoDisponibleException.class)
    ProblemDetail catalogoNoDisponible(CatalogoNoDisponibleException ex) {
        LOG.warn("ms-catalog no disponible: {}", ex.getCause() == null ? ex.getMessage() : ex.getCause().toString());
        ProblemDetail problema = Problemas.crear(HttpStatus.SERVICE_UNAVAILABLE, CodigoError.SERVICIO_NO_DISPONIBLE,
                "El servicio ms-catalog no está disponible en este momento");
        problema.setProperty("servicio", "ms-catalog");
        return problema;
    }

    /** Las de Spring Security las resuelven su entry point y su access denied handler, no este advice. */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void relanzarExcepcionDeSeguridad(RuntimeException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail errorInterno(Exception ex) {
        LOG.error("Error no controlado", ex);
        return Problemas.crear(HttpStatus.INTERNAL_SERVER_ERROR, CodigoError.ERROR_INTERNO,
                "Ocurrió un error inesperado");
    }

    /** Un cuerpo que no es JSON, o un campo con otro tipo (p. ej. un texto en precioTotalEsperado). */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problema = Problemas.crear(status, CodigoError.DATOS_INVALIDOS,
                "El cuerpo de la solicitud no es un JSON válido");
        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    /** Un parámetro con otro tipo (p. ej. pagina=abc). */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String parametro = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName()
                : ex.getPropertyName();
        ProblemDetail problema = Problemas.crear(HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS,
                "Datos de entrada inválidos");
        problema.setProperty("campos", Map.of(parametro == null ? "parametro" : parametro, "FORMATO"));
        return handleExceptionInternal(ex, problema, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Agrega un código genérico a los errores estándar de Spring MVC (404, 405, 415...). */
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
