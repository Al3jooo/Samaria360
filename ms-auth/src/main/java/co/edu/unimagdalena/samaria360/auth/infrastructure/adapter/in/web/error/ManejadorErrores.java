package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web.error;

import co.edu.unimagdalena.samaria360.auth.domain.exception.CambioRolPropioException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CredencialesInvalidasException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaBloqueadaException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaInactivaException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaNoElegibleException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaNoEncontradaException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaPendienteValidacionException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaRechazadaException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.DatosRegistroInvalidosException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.EstadoCambiadoException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.PoliticasNoAceptadasException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.RntYaRegistradoException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.RolCambiadoException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.UltimoAdministradorException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.UsuarioNoEncontradoException;
import co.edu.unimagdalena.samaria360.auth.domain.model.MotivoInvalido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Errores como ProblemDetail (RFC 9457) con un {@code codigo} estable. Extiende
 * ResponseEntityExceptionHandler para que los errores estándar de Spring MVC tengan el mismo formato.
 */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    /** Cuerpo idéntico para correo inexistente y contraseña incorrecta: no revela qué correos existen. */
    @ExceptionHandler(CredencialesInvalidasException.class)
    ProblemDetail credencialesInvalidas(CredencialesInvalidasException ex) {
        return Problemas.crear(HttpStatus.UNAUTHORIZED, CodigoError.CREDENCIALES_INVALIDAS, ex.getMessage());
    }

    @ExceptionHandler(CuentaBloqueadaException.class)
    ProblemDetail cuentaBloqueada(CuentaBloqueadaException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.LOCKED, CodigoError.CUENTA_BLOQUEADA, ex.getMessage());
        problema.setProperty("bloqueadoHasta", ex.getBloqueadoHasta());
        return problema;
    }

    @ExceptionHandler(CuentaInactivaException.class)
    ProblemDetail cuentaInactiva(CuentaInactivaException ex) {
        return Problemas.crear(HttpStatus.FORBIDDEN, CodigoError.CUENTA_INACTIVA, ex.getMessage());
    }

    /** Contraseña correcta, prestador Pendiente de Validación (RF02, decisión D3). */
    @ExceptionHandler(CuentaPendienteValidacionException.class)
    ProblemDetail cuentaPendienteValidacion(CuentaPendienteValidacionException ex) {
        return Problemas.crear(HttpStatus.FORBIDDEN, CodigoError.CUENTA_PENDIENTE_VALIDACION, ex.getMessage());
    }

    /** Contraseña correcta, prestador Rechazado (RF28, decisión D3). El modelo no guarda el motivo: mensaje genérico. */
    @ExceptionHandler(CuentaRechazadaException.class)
    ProblemDetail cuentaRechazada(CuentaRechazadaException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.FORBIDDEN, CodigoError.CUENTA_RECHAZADA, ex.getMessage());
        // DEC-273: la justificación del Administrador (RF28), solo después de verificar la contraseña.
        ex.getMotivo().ifPresent(motivo -> problema.setProperty("motivo", motivo));
        return problema;
    }

    /** Registro (RF01, RF02): un código por campo, con los nombres JSON de la API. El frontend los traduce (RF03). */
    @ExceptionHandler(DatosRegistroInvalidosException.class)
    ProblemDetail datosRegistroInvalidos(DatosRegistroInvalidosException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS,
                "Datos de entrada inválidos");
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getCampos().forEach((campo, motivo) -> campos.put(campo, motivo.name()));
        problema.setProperty("campos", campos);
        return problema;
    }

    @ExceptionHandler(PoliticasNoAceptadasException.class)
    ProblemDetail politicasNoAceptadas(PoliticasNoAceptadasException ex) {
        return Problemas.crear(HttpStatus.BAD_REQUEST, CodigoError.POLITICAS_NO_ACEPTADAS, ex.getMessage());
    }

    /** El RNT es un registro público: decir que ya existe no revela datos de terceros (ADR-006). */
    @ExceptionHandler(RntYaRegistradoException.class)
    ProblemDetail rntYaRegistrado(RntYaRegistradoException ex) {
        return Problemas.crear(HttpStatus.CONFLICT, CodigoError.RNT_YA_REGISTRADO, ex.getMessage());
    }

    /** Otro Administrador cambió la cuenta antes, o la acción no es una transición válida (DEC-39). */
    @ExceptionHandler(EstadoCambiadoException.class)
    ProblemDetail estadoCambiado(EstadoCambiadoException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.CONFLICT, CodigoError.ESTADO_CAMBIADO, ex.getMessage());
        problema.setProperty("estadoActual", ex.getEstadoActual().name());
        return problema;
    }

    /** Otro Administrador cambió el rol antes, o la lista estaba desactualizada (RF45, DEC-104). */
    @ExceptionHandler(RolCambiadoException.class)
    ProblemDetail rolCambiado(RolCambiadoException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.CONFLICT, CodigoError.ROL_CAMBIADO, ex.getMessage());
        problema.setProperty("rolActual", ex.getRolActual().name());
        return problema;
    }

    @ExceptionHandler(CuentaNoElegibleException.class)
    ProblemDetail cuentaNoElegible(CuentaNoElegibleException ex) {
        ProblemDetail problema = Problemas.crear(HttpStatus.CONFLICT, CodigoError.CUENTA_NO_ELEGIBLE, ex.getMessage());
        problema.setProperty("motivo", ex.getMotivo().name());
        return problema;
    }

    @ExceptionHandler(CambioRolPropioException.class)
    ProblemDetail cambioRolPropio(CambioRolPropioException ex) {
        return Problemas.crear(HttpStatus.CONFLICT, CodigoError.CAMBIO_ROL_PROPIO, ex.getMessage());
    }

    @ExceptionHandler(UltimoAdministradorException.class)
    ProblemDetail ultimoAdministrador(UltimoAdministradorException ex) {
        return Problemas.crear(HttpStatus.CONFLICT, CodigoError.ULTIMO_ADMINISTRADOR, ex.getMessage());
    }

    @ExceptionHandler(CuentaNoEncontradaException.class)
    ProblemDetail cuentaNoEncontrada(CuentaNoEncontradaException ex) {
        return Problemas.crear(HttpStatus.NOT_FOUND, CodigoError.CUENTA_NO_ENCONTRADA, ex.getMessage());
    }

    /** Token válido de un usuario que ya no existe. */
    @ExceptionHandler(UsuarioNoEncontradoException.class)
    ProblemDetail usuarioNoEncontrado(UsuarioNoEncontradoException ex) {
        return Problemas.crear(HttpStatus.UNAUTHORIZED, CodigoError.NO_AUTENTICADO, "La sesión no es válida");
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

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(error.getField(), motivo(error).name());
        }
        ProblemDetail problema = Problemas.crear(status, CodigoError.DATOS_INVALIDOS, "Datos de entrada inválidos");
        problema.setProperty("campos", campos);
        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    /** Bean Validation del login → el mismo vocabulario de códigos que el registro (nunca textos). */
    private static MotivoInvalido motivo(FieldError error) {
        String regla = error.getCode() == null ? "" : error.getCode();
        return switch (regla) {
            case "NotBlank", "NotNull", "NotEmpty" -> MotivoInvalido.REQUERIDO;
            case "Size" -> MotivoInvalido.LONGITUD_MAXIMA;
            default -> MotivoInvalido.FORMATO;
        };
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problema = Problemas.crear(status, CodigoError.DATOS_INVALIDOS,
                "El cuerpo de la solicitud no es un JSON válido");
        return handleExceptionInternal(ex, problema, headers, status, request);
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
