package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.web.error;

import co.edu.unimagdalena.samaria360.analytics.domain.exception.CamposInvalidosException;
import co.edu.unimagdalena.samaria360.analytics.domain.exception.ReglaNegocioException;
import co.edu.unimagdalena.samaria360.analytics.domain.exception.NotificacionNoEncontradaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/** Errores de la API de ms-analytics como ProblemDetail (RFC 9457) con {@code codigo}, como los demás servicios. */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(NotificacionNoEncontradaException.class)
    ProblemDetail notificacionNoEncontrada(NotificacionNoEncontradaException ex) {
        return Problemas.crear(HttpStatus.NOT_FOUND, CodigoError.NOTIFICACION_NO_ENCONTRADA, ex.getMessage());
    }

    @ExceptionHandler(CamposInvalidosException.class)
    ProblemDetail camposInvalidos(CamposInvalidosException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS,
                "Datos de entrada inválidos");
        problema.setProperty("campos", ex.getCampos());
        return problema;
    }

    /** Reglas de RF17, RF29 y RF57 (DEC-300, DEC-301): el código decide el estado HTTP. */
    @ExceptionHandler(ReglaNegocioException.class)
    ProblemDetail reglaNegocio(ReglaNegocioException ex) {
        CodigoError codigo = CodigoError.valueOf(ex.getCodigo());
        HttpStatus estado = switch (codigo) {
            case SIN_RESERVA_COMPLETADA, CUENTA_INACTIVA -> HttpStatus.FORBIDDEN;
            case CALIFICACION_NO_ENCONTRADA, REPORTE_NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.CONFLICT;
        };
        ProblemDetail problema = Problemas.crear(estado, codigo, ex.getMessage());
        ex.getExtra().forEach(problema::setProperty);
        return problema;
    }

    @ExceptionHandler(DatosInvalidosException.class)
    ProblemDetail datosInvalidos(DatosInvalidosException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS,
                "Datos de entrada inválidos");
        problema.setProperty("campos", ex.getCampos());
        return problema;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail tipoInvalido(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS,
                "Datos de entrada inválidos");
        problema.setProperty("campos", Map.of(ex.getName(), "FORMATO"));
        return problema;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail cuerpoInvalido(HttpMessageNotReadableException ex) {
        return Problemas.crear(HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS, "El cuerpo no es JSON válido");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex) {
        LOG.error("Error inesperado", ex);
        return Problemas.crear(HttpStatus.INTERNAL_SERVER_ERROR, CodigoError.ERROR_INTERNO, "Error interno");
    }
}
