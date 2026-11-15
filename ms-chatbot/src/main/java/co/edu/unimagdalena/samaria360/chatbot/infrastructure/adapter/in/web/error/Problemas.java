package co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.in.web.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Construcción de respuestas RFC 9457 con el campo {@code codigo}. */
final class Problemas {

    static final String CODIGO = "codigo";

    private Problemas() {
    }

    static ProblemDetail crear(HttpStatusCode estado, CodigoError codigo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setProperty(CODIGO, codigo.name());
        return problema;
    }

    /** Para los filtros de Spring Security, que responden antes de llegar a los controladores. */
    static void escribir(HttpServletRequest solicitud, HttpServletResponse respuesta, ObjectMapper json,
                         ProblemDetail problema) throws IOException {
        problema.setInstance(URI.create(solicitud.getRequestURI()));
        respuesta.setStatus(problema.getStatus());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(respuesta.getOutputStream(), problema);
    }
}
