package co.edu.unimagdalena.samaria360.catalog.application.service;

import co.edu.unimagdalena.samaria360.catalog.application.fake.OfertaRepositoryEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.domain.model.AtractivoPublico;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Categoria;
import co.edu.unimagdalena.samaria360.catalog.domain.model.CondicionAccesibilidad;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Coordenada;
import co.edu.unimagdalena.samaria360.catalog.domain.model.CriteriosBusqueda;
import co.edu.unimagdalena.samaria360.catalog.domain.model.DetalleCategoria;
import co.edu.unimagdalena.samaria360.catalog.domain.model.DetalleTour;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EstadoOperativo;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EstadoPublicacion;
import co.edu.unimagdalena.samaria360.catalog.domain.model.FiltrosAlojamiento;
import co.edu.unimagdalena.samaria360.catalog.domain.model.FiltrosCategoria;
import co.edu.unimagdalena.samaria360.catalog.domain.model.OrdenBusqueda;
import co.edu.unimagdalena.samaria360.catalog.domain.model.PaginaResultados;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ResultadoOferta;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioComercial;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioIncluido;
import co.edu.unimagdalena.samaria360.catalog.domain.model.TipoOferta;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Unidades;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Caso de uso con el repositorio en memoria, que aplica PoliticaBusqueda: una prueba por cada decisión de
 * docs/trazabilidad.md, más el orden por defecto.
 */
class BuscarOfertaServiceTest {

    private static final UUID PRESTADOR = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID GESTOR = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final Coordenada PARQUE_DE_LOS_NOVIOS = new Coordenada(-74.2117, 11.2420);
    private static final Coordenada CENTRO = new Coordenada(-74.2125, 11.2425);
    private static final Coordenada MINCA = new Coordenada(-74.1185, 11.1430);
    private static final Set<CondicionAccesibilidad> ACCESIBLE = Set.of(CondicionAccesibilidad.MovilidadReducida);

    private final ServicioComercial hotel = servicio("s1", "Hotel Casa Tayrona", alojamiento("150000"), "150000", CENTRO,
            EstadoPublicacion.Publicado, true, ACCESIBLE);
    private final ServicioComercial tourCafe = servicio("s2", "Tour de Café en Minca", tour(), "80000", MINCA,
            EstadoPublicacion.Publicado, true, Set.of());
    private final ServicioComercial cabanas = servicio("s3", "Cabañas El Roble", alojamiento("180000"), "180000", MINCA,
            EstadoPublicacion.Publicado, true, Set.of());
    private final ServicioComercial pendiente = servicio("s4", "Alquiler de kayaks", tour(), "40000", CENTRO,
            EstadoPublicacion.Pendiente, true, Set.of());
    private final ServicioComercial deInactivo = servicio("s5", "Snorkel en Bahía Concha", tour(), "95000", CENTRO,
            EstadoPublicacion.Publicado, false, Set.of());
    private final AtractivoPublico catedral = atractivo("a1", "Catedral Basílica", CENTRO, EstadoOperativo.Abierto, ACCESIBLE);
    private final AtractivoPublico cascadaCerrada = atractivo("a2", "Cascada El Salto", MINCA,
            EstadoOperativo.Cerrado_temporalmente, Set.of());
    private final AtractivoPublico mirador = atractivo("a3", "Mirador de Taganga", CENTRO,
            EstadoOperativo.En_mantenimiento, Set.of());

    private final OfertaRepositoryEnMemoria repositorio = new OfertaRepositoryEnMemoria()
            .con(hotel, tourCafe, cabanas, pendiente, deInactivo)
            .con(catedral, cascadaCerrada, mirador);
    private final BuscarOfertaService servicio = new BuscarOfertaService(repositorio);

    @Test
    void decision1_losAtractivosAparecenJuntoConLosServicios() {
        PaginaResultados<ResultadoOferta> pagina = servicio.buscar(CriteriosBusqueda.sinFiltros());

        assertThat(pagina.elementos()).extracting(ResultadoOferta::tipo)
                .contains(TipoOferta.Servicio, TipoOferta.Atractivo);
        assertThat(pagina.totalElementos()).isEqualTo(6); // 3 servicios visibles + 3 atractivos
    }

    @Test
    void soloSeMuestranServiciosPublicadosDePrestadoresActivos() {
        assertThat(ids(servicio.buscar(CriteriosBusqueda.sinFiltros()))).doesNotContain("s4", "s5");
    }

    @Test
    void decision2_conCategoriaLosAtractivosQuedanFuera() {
        PaginaResultados<ResultadoOferta> pagina = servicio.buscar(criterios(null, Categoria.Tour, null, null, null, Set.of()));

        assertThat(ids(pagina)).containsExactly("s2");
    }

    @Test
    void masFiltrosDejaSoloLosServiciosDeLaCategoriaQueCumplenSuDetalle() {
        ServicioComercial cabanaWifi = servicio("s6", "Cabaña con WiFi",
                Unidades.alojamiento("Cabaña", 3, 4, "200000", List.of("WiFi", "Desayuno")), "200000", MINCA,
                EstadoPublicacion.Publicado, true, Set.of());
        OfertaRepositoryEnMemoria conCabana = new OfertaRepositoryEnMemoria()
                .con(hotel, cabanas, tourCafe, cabanaWifi)
                .con(catedral);
        FiltrosCategoria wifiParaTres = new FiltrosAlojamiento(Set.of(ServicioIncluido.WiFi), 3, null);

        PaginaResultados<ResultadoOferta> pagina = new BuscarOfertaService(conCabana).buscar(new CriteriosBusqueda(
                null, Categoria.Alojamiento, wifiParaTres, null, null, null, Set.of(), null, 0, 10));

        assertThat(ids(pagina)).containsExactly("s6");
        assertThat(conCabana.ultimosCriterios().filtrosCategoria()).isEqualTo(wifiParaTres);
    }

    @Test
    void decision3_elPrecioMaximoExcluyeLosAtractivos() {
        PaginaResultados<ResultadoOferta> pagina = servicio.buscar(
                criterios(null, null, new BigDecimal("150000"), null, null, Set.of()));

        assertThat(ids(pagina)).containsExactlyInAnyOrder("s1", "s2");
        assertThat(pagina.elementos()).extracting(ResultadoOferta::tipo).containsOnly(TipoOferta.Servicio);
    }

    @Test
    void decision4_alOrdenarPorPrecioLosResultadosSinPrecioVanAlFinal() {
        List<String> ascendente = ids(servicio.buscar(criterios(null, null, null, null, OrdenBusqueda.PrecioAscendente, Set.of())));
        List<String> descendente = ids(servicio.buscar(criterios(null, null, null, null, OrdenBusqueda.PrecioDescendente, Set.of())));

        assertThat(ascendente).containsExactly("s2", "s1", "s3", "a1", "a2", "a3");
        assertThat(descendente).containsExactly("s3", "s1", "s2", "a1", "a2", "a3");
    }

    @Test
    void decision5_losAtractivosCerradosOEnMantenimientoAparecenConSuEstado() {
        PaginaResultados<ResultadoOferta> pagina = servicio.buscar(CriteriosBusqueda.sinFiltros());

        assertThat(pagina.elementos()).filteredOn(r -> r.tipo() == TipoOferta.Atractivo)
                .extracting(ResultadoOferta::estadoOperativo)
                .containsExactlyInAnyOrder(EstadoOperativo.Abierto, EstadoOperativo.Cerrado_temporalmente,
                        EstadoOperativo.En_mantenimiento);
    }

    @Test
    void decision6_elFiltroDeAccesibilidadAplicaAAmbos() {
        PaginaResultados<ResultadoOferta> pagina = servicio.buscar(criterios(null, null, null, null, null, ACCESIBLE));

        assertThat(ids(pagina)).containsExactlyInAnyOrder("s1", "a1");
    }

    @Test
    void sinOrdenYConUbicacionOrdenaPorDistancia() {
        PaginaResultados<ResultadoOferta> pagina = servicio.buscar(
                criterios(null, null, null, PARQUE_DE_LOS_NOVIOS, null, Set.of()));

        assertThat(repositorio.ultimosCriterios().orden()).isEqualTo(OrdenBusqueda.Distancia);
        assertThat(pagina.elementos()).extracting(ResultadoOferta::distanciaKm).isSorted().doesNotContainNull();
        // Primero los del centro (mismo punto: desempate por id), después los de Minca.
        assertThat(ids(pagina)).containsExactly("a1", "a3", "s1", "a2", "s2", "s3");
    }

    @Test
    void sinOrdenNiUbicacionOrdenaPorNombreSinDistinguirTildes() {
        PaginaResultados<ResultadoOferta> pagina = servicio.buscar(CriteriosBusqueda.sinFiltros());

        assertThat(repositorio.ultimosCriterios().orden()).isEqualTo(OrdenBusqueda.Nombre);
        assertThat(pagina.elementos()).extracting(ResultadoOferta::nombre).containsExactly(
                "Cabañas El Roble", "Cascada El Salto", "Catedral Basílica", "Hotel Casa Tayrona",
                "Mirador de Taganga", "Tour de Café en Minca");
    }

    @Test
    void unOrdenExplicitoSeRespeta() {
        servicio.buscar(criterios(null, null, null, null, OrdenBusqueda.PrecioDescendente, Set.of()));

        assertThat(repositorio.ultimosCriterios().orden()).isEqualTo(OrdenBusqueda.PrecioDescendente);
    }

    @Test
    void elFiltroDeDistanciaDejaFueraLoLejano() {
        PaginaResultados<ResultadoOferta> pagina = servicio.buscar(new CriteriosBusqueda(null, null, null,
                PARQUE_DE_LOS_NOVIOS, 2.0, Set.of(), null, 0, 10));

        assertThat(ids(pagina)).containsExactlyInAnyOrder("s1", "a1", "a3");
    }

    @Test
    void elTextoNoDistingueMayusculasNiTildes() {
        assertThat(ids(servicio.buscar(criterios("CAFÉ", null, null, null, null, Set.of())))).containsExactly("s2");
        // DEC-274: sin tilde también coincide.
        assertThat(ids(servicio.buscar(criterios("cafe", null, null, null, null, Set.of())))).containsExactly("s2");
    }

    @Test
    void laPaginacionNoRepiteNiOmiteElementos() {
        List<String> todos = ids(servicio.buscar(CriteriosBusqueda.sinFiltros()));
        List<String> paginados = new java.util.ArrayList<>();
        for (int pagina = 0; pagina < 3; pagina++) {
            paginados.addAll(ids(servicio.buscar(new CriteriosBusqueda(null, null, null, null, null, Set.of(), null,
                    pagina, 2))));
        }

        assertThat(paginados).containsExactlyElementsOf(todos);
    }

    // ------------------------------------------------------------------ auxiliares

    private static CriteriosBusqueda criterios(String texto, Categoria categoria, BigDecimal precioMaximo,
                                               Coordenada ubicacion, OrdenBusqueda orden,
                                               Set<CondicionAccesibilidad> accesibilidad) {
        return new CriteriosBusqueda(texto, categoria, precioMaximo, ubicacion, null, accesibilidad, orden, 0, 10);
    }

    private static List<String> ids(PaginaResultados<ResultadoOferta> pagina) {
        return pagina.elementos().stream().map(ResultadoOferta::id).toList();
    }

    /** Una sola unidad: el precio por noche es el precio «desde» del servicio. */
    private static DetalleCategoria alojamiento(String precioNoche) {
        return Unidades.alojamiento("Doble", 10, 2, precioNoche, List.of());
    }

    private static DetalleCategoria tour() {
        return new DetalleTour("Muelle", new BigDecimal("3"), "Media", 10);
    }

    private static ServicioComercial servicio(String id, String nombre, DetalleCategoria detalle, String precio,
                                              Coordenada coordenada, EstadoPublicacion estado, boolean prestadorActivo,
                                              Set<CondicionAccesibilidad> accesibilidad) {
        return new ServicioComercial(id, PRESTADOR, nombre, detalle.categoria(), detalle, "Descripción de " + nombre,
                coordenada, null, estado, prestadorActivo, accesibilidad, null, new BigDecimal(precio), List.of(), null, null, null, null, null);
    }

    private static AtractivoPublico atractivo(String id, String nombre, Coordenada coordenada, EstadoOperativo estado,
                                              Set<CondicionAccesibilidad> accesibilidad) {
        return new AtractivoPublico(id, GESTOR, nombre, coordenada, "Descripción de " + nombre, "Naturaleza",
                List.of(), 100, estado, null, accesibilidad);
    }
}
