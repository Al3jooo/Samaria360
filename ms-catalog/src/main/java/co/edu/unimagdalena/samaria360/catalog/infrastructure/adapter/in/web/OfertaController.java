package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.BuscarOfertaUseCase;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.CriteriosBusquedaInvalidosException;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Categoria;
import co.edu.unimagdalena.samaria360.catalog.domain.model.CondicionAccesibilidad;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Coordenada;
import co.edu.unimagdalena.samaria360.catalog.domain.model.CriteriosBusqueda;
import co.edu.unimagdalena.samaria360.catalog.domain.model.OrdenBusqueda;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ParametrosFiltrosCategoria;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.dto.RespuestaPaginaOferta;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Buscar oferta turística (WF-04, WF-05). Pública: se puede explorar sin iniciar sesión. */
@RestController
@RequestMapping("/api/v1/catalogo")
public class OfertaController {

    private final BuscarOfertaUseCase buscarOferta;

    public OfertaController(BuscarOfertaUseCase buscarOferta) {
        this.buscarOferta = buscarOferta;
    }

    /**
     * @param lat           latitud de la ubicación del turista (RF38); va junto con {@code lng}
     * @param accesibilidad repetible: MovilidadReducida, Visual, Auditiva (RF50)
     * @param orden         Distancia, PrecioAscendente, PrecioDescendente, Nombre o Calificacion (RF61, DEC-302); por defecto, distancia si
     *                      hay ubicación y nombre si no
     * @param serviciosIncluidos repetible: WiFi, Desayuno, Piscina, Parqueadero. Este y los siguientes son
     *                      «Más filtros» (RF10): cada uno exige su categoría (serviciosIncluidos, huespedes y
     *                      tipoHabitacion → Alojamiento; menuVegetariano y tipoCocina → Gastronomia;
     *                      nivelDificultad y duracionMaxHoras → Tour; tipoVehiculoEquipo → AlquilerEquipo)
     */
    @GetMapping("/oferta")
    public RespuestaPaginaOferta buscar(
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Categoria categoria,
            @RequestParam(required = false) BigDecimal precioMax,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double distanciaKm,
            @RequestParam(required = false) List<CondicionAccesibilidad> accesibilidad,
            @RequestParam(required = false) OrdenBusqueda orden,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "" + CriteriosBusqueda.TAMANO_PAGINA_MAXIMO) int tamano,
            @RequestParam(required = false) List<String> serviciosIncluidos,
            @RequestParam(required = false) Integer huespedes,
            @RequestParam(required = false) String tipoHabitacion,
            @RequestParam(required = false) Boolean menuVegetariano,
            @RequestParam(required = false) String tipoCocina,
            @RequestParam(required = false) String nivelDificultad,
            @RequestParam(required = false) BigDecimal duracionMaxHoras,
            @RequestParam(required = false) String tipoVehiculoEquipo) {
        ParametrosFiltrosCategoria masFiltros = new ParametrosFiltrosCategoria(serviciosIncluidos, huespedes,
                tipoHabitacion, menuVegetariano, tipoCocina, nivelDificultad, duracionMaxHoras, tipoVehiculoEquipo);
        CriteriosBusqueda criterios = new CriteriosBusqueda(texto, categoria, masFiltros.paraCategoria(categoria),
                precioMax, ubicacion(lat, lng), distanciaKm,
                accesibilidad == null ? Set.of() : Set.copyOf(accesibilidad), orden, pagina, tamano);
        return RespuestaPaginaOferta.de(buscarOferta.buscar(criterios));
    }

    private static Coordenada ubicacion(Double lat, Double lng) {
        if (lat == null && lng == null) {
            return null;
        }
        if (lat == null || lng == null) {
            throw new CriteriosBusquedaInvalidosException("La ubicación exige lat y lng juntos");
        }
        try {
            return new Coordenada(lng, lat);
        } catch (IllegalArgumentException e) {
            throw new CriteriosBusquedaInvalidosException(e.getMessage());
        }
    }
}
