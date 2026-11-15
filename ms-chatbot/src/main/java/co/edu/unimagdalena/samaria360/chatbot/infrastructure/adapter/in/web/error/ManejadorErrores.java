package co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.in.web.error;

import co.edu.unimagdalena.samaria360.chatbot.domain.exception.LimiteSuperadoException;
import co.edu.unimagdalena.samaria360.chatbot.domain.exception.RecursoNoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/** Errores de la API de ms-chatbot como ProblemDetail (RFC 9457) con {@code codigo}, como los demás servicios. */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(co.edu.unimagdalena.samaria360.chatbot.domain.exception.DatosInvalidosException.class)
    ProblemDetail datosInvalidos(co.edu.unimagdalena.samaria360.chatbot.domain.exception.DatosInvalidosException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS,
                "Datos de entrada inválidos");
        problema.setProperty("campos", ex.getCampos());
        return problema;
    }

    @ExceptionHandler(DatosInvalidosException.class)
    ProblemDetail parametroInvalido(DatosInvalidosException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS,
                "Datos de entrada inválidos");
        problema.setProperty("campos", ex.getCampos());
        return problema;
    }

    @ExceptionHandler(LimiteSuperadoException.class)
    ProblemDetail limite(LimiteSuperadoException ex) {
        CodigoError codigo = CodigoError.valueOf(ex.getCodigo());
        HttpStatus estado = codigo == CodigoError.LIMITE_MENSAJES ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.CONFLICT;
        return Problemas.crear(estado, codigo, "Se alcanzó el límite");
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ProblemDetail noEncontrado(RecursoNoEncontradoException ex) {
        return Problemas.crear(HttpStatus.NOT_FOUND, CodigoError.valueOf(ex.getCodigo()), "No encontrado");
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
        LOG.error("Error inesperado: {}", ex.getClass().getSimpleName());
        return Problemas.crear(HttpStatus.INTERNAL_SERVER_ERROR, CodigoError.ERROR_INTERNO, "Error interno");
    }
}
