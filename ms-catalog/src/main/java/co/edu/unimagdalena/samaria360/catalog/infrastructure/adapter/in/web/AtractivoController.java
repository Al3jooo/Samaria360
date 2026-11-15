package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultasFichasUseCase;
import co.edu.unimagdalena.samaria360.catalog.domain.model.AtractivoPublico;
import org.springframework.web.bind.annotation.RequestHeader;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.AfluenciaUseCase;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto.RespuestaAfluencia;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultarAtractivoUseCase;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto.RespuestaAtractivo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CU «Consultar detalle de atractivo» (DEC-217): la ficha pública (WF-06 para atractivos), sin token. Un atractivo es
 * visible mientras exista (DEC-87).
 */
@RestController
@RequestMapping("/api/v1/catalogo/atractivos")
public class AtractivoController {

    private final ConsultarAtractivoUseCase consultar;
    private final AfluenciaUseCase afluencia;
    private final ConsultasFichasUseCase consultas;

    public AtractivoController(ConsultarAtractivoUseCase consultar, AfluenciaUseCase afluencia,
                               ConsultasFichasUseCase consultas) {
        this.consultar = consultar;
        this.afluencia = afluencia;
        this.consultas = consultas;
    }

    /** RF39, RF08 y RF51 (DEC-304, DEC-305): el nivel vigente y los destinos alternativos, públicos. */
    @GetMapping("/{id}/afluencia")
    public RespuestaAfluencia afluencia(@PathVariable String id) {
        return RespuestaAfluencia.de(afluencia.consultar(id));
    }

    @GetMapping("/{id}")
    public RespuestaAtractivo detalle(@PathVariable String id,
                                      @RequestHeader(value = "X-Forwarded-For", required = false) String reenviadoPor) {
        AtractivoPublico atractivo = consultar.detalle(id);
        // DEC-307: solo las visitas que llegan por el gateway (RF14, top 3 de atractivos más consultados).
        if (reenviadoPor != null) {
            consultas.registrar("atractivo", atractivo.getId().orElse(id), atractivo.getNombre(), null);
        }
        return RespuestaAtractivo.de(atractivo);
    }
}
