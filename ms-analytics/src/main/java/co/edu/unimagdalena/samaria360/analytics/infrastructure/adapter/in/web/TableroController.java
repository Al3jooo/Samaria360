package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.PanelPrestador;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.ReporteAfluencia;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.TableroGeneral;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Tableros y reportes (DEC-308; contrato en {@code docs/sad/api/ms-analytics.md}): RF14 (Administrador y Gestor), RF31
 * (el Prestador del token) y RF64 (Gestor). Las fechas son días de Santa Marta ({@code yyyy-MM-dd}, {@code hasta}
 * incluido).
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class TableroController {

    private final TablerosUseCase tableros;

    public TableroController(TablerosUseCase tableros) {
        this.tableros = tableros;
    }

    @GetMapping("/tableros/general")
    public TableroGeneral general(@RequestParam(required = false) String desde,
                                  @RequestParam(required = false) String hasta) {
        return tableros.general(desde, hasta);
    }

    @GetMapping("/prestador/panel")
    public PanelPrestador panel(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Integer dias) {
        return tableros.prestador(UUID.fromString(jwt.getSubject()), dias);
    }

    @GetMapping("/gestor/afluencia")
    public ReporteAfluencia afluencia(@RequestParam(required = false) String desde,
                                      @RequestParam(required = false) String hasta,
                                      @RequestParam(required = false) String atractivo) {
        return tableros.afluencia(desde, hasta, atractivo);
    }
}
