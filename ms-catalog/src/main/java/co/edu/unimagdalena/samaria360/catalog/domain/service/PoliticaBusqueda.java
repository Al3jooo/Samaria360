package co.edu.unimagdalena.samaria360.catalog.domain.service;

import co.edu.unimagdalena.samaria360.catalog.domain.model.AtractivoPublico;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Coordenada;
import co.edu.unimagdalena.samaria360.catalog.domain.model.CriteriosBusqueda;
import co.edu.unimagdalena.samaria360.catalog.domain.model.DetalleAlojamiento;
import co.edu.unimagdalena.samaria360.catalog.domain.model.FiltrosAlojamiento;
import co.edu.unimagdalena.samaria360.catalog.domain.model.OrdenBusqueda;
import co.edu.unimagdalena.samaria360.catalog.domain.model.PaginaResultados;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ResultadoOferta;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioComercial;
import co.edu.unimagdalena.samaria360.catalog.domain.model.TextoBusqueda;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reglas de la búsqueda combinada de servicios y atractivos (RF10, RF16, RF23, RF33, RF38, RF50, RF61), según las
 * decisiones registradas en docs/trazabilidad.md:
 * <ol>
 *   <li>los atractivos aparecen junto con los servicios;</li>
 *   <li>el filtro de categoría aplica solo a servicios: con categoría, los atractivos quedan fuera;</li>
 *   <li>el filtro de precio máximo excluye los atractivos (no tienen precio);</li>
 *   <li>al ordenar por precio, los resultados sin precio van al final;</li>
 *   <li>los atractivos cerrados o en mantenimiento aparecen, con su estado;</li>
 *   <li>el filtro de accesibilidad aplica a ambos.</li>
 * </ol>
 * «Más filtros» ({@link co.edu.unimagdalena.samaria360.catalog.domain.model.FiltrosCategoria}) exige categoría, así
 * que, por la decisión 2, nunca incluye atractivos.
 * Es la referencia en Java de esas reglas: el adaptador MongoDB las traduce a una agregación (ADR-004) y sus
 * pruebas verifican que el resultado coincida.
 */
public final class PoliticaBusqueda {

    /** Decisiones 1, 2 y 3. */
    public boolean incluyeAtractivos(CriteriosBusqueda criterios) {
        return criterios.categoria() == null && criterios.precioMaximo() == null;
    }

    /** Un servicio aparece si es visible (RF16, RF22) y cumple los filtros; devuelve su resultado con distancia. */
    public Optional<ResultadoOferta> evaluar(ServicioComercial servicio, CriteriosBusqueda criterios) {
        if (!servicio.esVisibleEnBusquedaPublica()) {
            return Optional.empty();
        }
        if (criterios.categoria() != null && servicio.getCategoria() != criterios.categoria()) {
            return Optional.empty();
        }
        if (criterios.filtrosCategoria() != null
                && !criterios.filtrosCategoria().cumple(servicio.getDetalle(), criterios.precioMaximo())) {
            return Optional.empty();
        }
        if (criterios.precioMaximo() != null) {
            BigDecimal precio = servicio.getPrecioBase().orElse(null);
            if (precio == null || precio.compareTo(criterios.precioMaximo()) > 0) {
                return Optional.empty();
            }
        }
        if (!coincideTexto(servicio.getNombreComercial(), servicio.getDescripcion().orElse(null), criterios)
                || !servicio.getCondicionesAccesibilidad().containsAll(criterios.accesibilidad())) {
            return Optional.empty();
        }
        Cercania cercania = cercania(criterios, servicio.getCoordenada());
        return cercania.dentro()
                ? Optional.of(ResultadoOferta.deServicio(servicio, cercania.km(), precioMostrado(servicio, criterios)))
                : Optional.empty();
    }

    /**
     * Precio «desde» de la tarjeta (D1): con filtros de unidad, el menor de las unidades que los cumplen (el mismo que
     * calcula el $filter del adaptador); si no, el del servicio. El orden por precio usa este valor.
     */
    private static BigDecimal precioMostrado(ServicioComercial servicio, CriteriosBusqueda criterios) {
        if (criterios.filtrosCategoria() instanceof FiltrosAlojamiento filtros && filtros.filtraUnidades()
                && servicio.getDetalle() instanceof DetalleAlojamiento alojamiento) {
            return filtros.precioDesde(alojamiento, criterios.precioMaximo());
        }
        return servicio.getPrecioBase().orElse(null);
    }

    /** Decisiones 1, 5 y 6: un atractivo aparece sin importar su estado operativo. */
    public Optional<ResultadoOferta> evaluar(AtractivoPublico atractivo, CriteriosBusqueda criterios) {
        if (!incluyeAtractivos(criterios)
                || !coincideTexto(atractivo.getNombre(), atractivo.getDescripcion().orElse(null), criterios)
                || !atractivo.getCondicionesAccesibilidad().containsAll(criterios.accesibilidad())) {
            return Optional.empty();
        }
        Cercania cercania = cercania(criterios, atractivo.getCoordenada());
        return cercania.dentro() ? Optional.of(ResultadoOferta.deAtractivo(atractivo, cercania.km())) : Optional.empty();
    }

    /** Orden con desempate estable por id (sin repetidos ni faltantes entre páginas). Decisión 4 en el precio. */
    public Comparator<ResultadoOferta> comparador(OrdenBusqueda orden) {
        Comparator<ResultadoOferta> principal = switch (orden == null ? OrdenBusqueda.Nombre : orden) {
            case Distancia -> Comparator.comparing(ResultadoOferta::distanciaKm,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case PrecioAscendente -> Comparator.comparing(ResultadoOferta::precio,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case PrecioDescendente -> Comparator.comparing(ResultadoOferta::precio,
                    Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder()));
            case Nombre -> Comparator.comparing(ResultadoOferta::nombre, colacionEspanol());
        };
        return principal.thenComparing(ResultadoOferta::id);
    }

    /** Búsqueda completa en memoria: filtra, ordena y pagina. */
    public PaginaResultados<ResultadoOferta> buscar(List<ServicioComercial> servicios, List<AtractivoPublico> atractivos,
                                                    CriteriosBusqueda criterios) {
        List<ResultadoOferta> resultados = new ArrayList<>();
        servicios.forEach(servicio -> evaluar(servicio, criterios).ifPresent(resultados::add));
        atractivos.forEach(atractivo -> evaluar(atractivo, criterios).ifPresent(resultados::add));
        resultados.sort(comparador(criterios.orden()));

        int desde = Math.min(criterios.pagina() * criterios.tamanoPagina(), resultados.size());
        int hasta = Math.min(desde + criterios.tamanoPagina(), resultados.size());
        return new PaginaResultados<>(resultados.subList(desde, hasta), criterios.pagina(), criterios.tamanoPagina(),
                resultados.size());
    }

    /** Texto sobre nombre y descripción, sin distinguir mayúsculas ni tildes (DEC-274, {@link TextoBusqueda}). */
    private static boolean coincideTexto(String nombre, String descripcion, CriteriosBusqueda criterios) {
        if (criterios.texto() == null) {
            return true;
        }
        return TextoBusqueda.contiene(nombre, criterios.texto()) || TextoBusqueda.contiene(descripcion, criterios.texto());
    }

    /** Distancia al turista (null sin ubicación) y si queda dentro de la distancia máxima (RF38). */
    private record Cercania(boolean dentro, Double km) {
    }

    private static Cercania cercania(CriteriosBusqueda criterios, Coordenada punto) {
        if (criterios.ubicacion() == null) {
            return new Cercania(true, null);
        }
        double km = criterios.ubicacion().distanciaKm(punto);
        return new Cercania(criterios.distanciaMaximaKm() == null || km <= criterios.distanciaMaximaKm(), km);
    }

    /** Colación en español de nivel primario: no distingue mayúsculas ni tildes, como la colación «es» de MongoDB. */
    private static Collator colacionEspanol() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("es"));
        collator.setStrength(Collator.PRIMARY);
        return collator;
    }
}
