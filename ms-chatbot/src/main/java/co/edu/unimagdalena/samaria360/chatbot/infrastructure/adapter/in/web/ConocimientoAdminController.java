package co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.chatbot.application.port.in.GestionarConocimientoUseCase;
import co.edu.unimagdalena.samaria360.chatbot.application.port.in.GestionarConocimientoUseCase.PaginaConocimiento;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.EntradaConocimiento;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** RF05 (contrato en {@code docs/sad/api/ms-chatbot.md}): la base de conocimientos, solo para el Administrador. */
@RestController
@RequestMapping("/api/v1/chatbot/admin/conocimiento")
public class ConocimientoAdminController {

    public record SolicitudConocimiento(String contenido, String categoria) {
    }

    public record RespuestaPagina(List<EntradaConocimiento> elementos, int pagina, int tamano, long totalElementos) {
        static RespuestaPagina de(PaginaConocimiento p) {
            return new RespuestaPagina(p.elementos(), p.pagina(), p.tamano(), p.totalElementos());
        }
    }

    private final GestionarConocimientoUseCase conocimiento;

    public ConocimientoAdminController(GestionarConocimientoUseCase conocimiento) {
        this.conocimiento = conocimiento;
    }

    @GetMapping
    public RespuestaPagina listar(@RequestParam(defaultValue = "0") int pagina) {
        return RespuestaPagina.de(conocimiento.listar(pagina));
    }

    @PostMapping
    public ResponseEntity<EntradaConocimiento> crear(@RequestBody SolicitudConocimiento solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conocimiento.crear(solicitud.contenido(), solicitud.categoria()));
    }

    @PutMapping("/{id}")
    public EntradaConocimiento actualizar(@PathVariable String id, @RequestBody SolicitudConocimiento solicitud) {
        return conocimiento.actualizar(id, solicitud.contenido(), solicitud.categoria());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        conocimiento.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
