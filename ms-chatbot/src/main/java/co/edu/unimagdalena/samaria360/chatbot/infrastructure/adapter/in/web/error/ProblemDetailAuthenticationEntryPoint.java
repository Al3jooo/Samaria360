package co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.in.web.error;

import co.edu.unimagdalena.samaria360.chatbot.infrastructure.config.ValidadorVersionSesion;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 con token ausente, inválido o vencido. Conserva la cabecera {@code WWW-Authenticate: Bearer}
 * (RFC 6750) y agrega el cuerpo ProblemDetail con {@code codigo: NO_AUTENTICADO}.
 */
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    private final ObjectMapper json;

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        bearer.commence(request, response, authException);
        ProblemDetail problema = sesionInvalidada(authException)
                ? Problemas.crear(HttpStatus.UNAUTHORIZED, CodigoError.SESION_INVALIDADA,
                        "La cuenta cambió después de iniciar sesión: inicia sesión de nuevo")
                : Problemas.crear(HttpStatus.UNAUTHORIZED, CodigoError.NO_AUTENTICADO,
                        "Se requiere un token de acceso válido");
        Problemas.escribir(request, response, json, problema);
    }

    /** El validador de {@code ver} deja el código {@code sesion_invalidada} en la causa del error (DEC-29). */
    private static boolean sesionInvalidada(AuthenticationException error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof JwtValidationException validacion && validacion.getErrors().stream()
                    .anyMatch(e -> ValidadorVersionSesion.SESION_INVALIDADA.equals(e.getErrorCode()))) {
                return true;
            }
        }
        return false;
    }
}
