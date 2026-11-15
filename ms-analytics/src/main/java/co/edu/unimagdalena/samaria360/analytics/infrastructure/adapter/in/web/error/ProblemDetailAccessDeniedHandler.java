package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.web.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 403 por falta de permisos del rol (RNF06), con el mismo formato ProblemDetail que el resto de errores. */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final AccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
    private final ObjectMapper json;

    public ProblemDetailAccessDeniedHandler(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        bearer.handle(request, response, accessDeniedException);
        Problemas.escribir(request, response, json, Problemas.crear(HttpStatus.FORBIDDEN,
                CodigoError.ACCESO_DENEGADO, "No tiene permisos para acceder a este recurso"));
    }
}
