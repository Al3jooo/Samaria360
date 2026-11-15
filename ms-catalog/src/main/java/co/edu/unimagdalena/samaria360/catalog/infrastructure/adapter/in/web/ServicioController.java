package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultasFichasUseCase;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioComercial;
import org.springframework.web.bind.annotation.RequestHeader;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultarServicioUseCase;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto.RespuestaServicio;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Detalle de un servicio (WF-06, WF-10). Público, como la búsqueda: no tiene privilegios para la red interna; ms-booking
 * lo consulta igual que cualquier cliente (ADR-007, Zero-Trust).
 */
@RestController
@RequestMapping("/api/v1/catalogo")
public class ServicioController {

    private final ConsultarServicioUseCase consultarServicio;
    private final ConsultasFichasUseCase consultas;

    public ServicioController(ConsultarServicioUseCase consultarServicio, ConsultasFichasUseCase consultas) {
        this.consultarServicio = consultarServicio;
        this.consultas = consultas;
    }

    /** 404 OFERTA_NO_DISPONIBLE si el id no es válido, no existe o el servicio no es visible (RF16, RF22). */
    @GetMapping("/servicios/{id}")
    public RespuestaServicio consultar(@PathVariable String id,
                                       @RequestHeader(value = "X-Forwarded-For", required = false) String reenviadoPor) {
        ServicioComercial servicio = consultarServicio.consultar(id);
        // DEC-307: solo las visitas que llegan por el gateway (las de ms-booking a la red interna no lo traen).
        if (reenviadoPor != null) {
            consultas.registrar("servicio", servicio.getId().orElse(id), servicio.getNombreComercial(),
                    servicio.getPrestadorId());
        }
        return RespuestaServicio.de(servicio);
    }
}
