package co.edu.unimagdalena.samaria360.catalog.infrastructure;

import co.edu.unimagdalena.samaria360.catalog.application.port.out.OfertaRepositoryPort;
import co.edu.unimagdalena.samaria360.catalog.domain.model.AtractivoPublico;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Coordenada;
import co.edu.unimagdalena.samaria360.catalog.domain.model.CriteriosBusqueda;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EstadoOperativo;
import co.edu.unimagdalena.samaria360.catalog.domain.model.OrdenBusqueda;
import co.edu.unimagdalena.samaria360.catalog.domain.model.PaginaResultados;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ResultadoOferta;
import co.edu.unimagdalena.samaria360.catalog.domain.service.PoliticaBusqueda;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.out.persistence.mongo.OfertaMongoAdapter;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RNF07: «integrar nuevas fuentes de datos turísticos sin requerir modificaciones en los módulos existentes».
 * Demostración de punta a punta: una fuente nueva (aquí, un listado externo de atractivos, como un conjunto de datos
 * abiertos) se suma a la búsqueda pública con <b>solo</b> un adaptador nuevo que implementa {@link OfertaRepositoryPort}
 * y un bean que lo registra. El controlador, {@code BuscarOfertaService}, {@code PoliticaBusqueda}, el adaptador de
 * MongoDB y {@code BeanConfig} no cambian: este archivo es lo único que se agrega. La regla que impide acoplarse al
 * adaptador concreto está en {@code NuevasFuentesDeDatosTest} (ArchUnit).
 */
@PruebaIntegracion
class NuevaFuenteDeDatosIntegracionTest {

    static final AtractivoPublico ATRACTIVO_EXTERNO = new AtractivoPublico("externo-aves-1", UUID.randomUUID(),
            "Avistamiento de aves en San Lorenzo", new Coordenada(-74.06, 11.11),
            "Ruta de aves endémicas en la Sierra Nevada, de una fuente de datos externa", "Naturaleza", List.of(),
            40, EstadoOperativo.Abierto, null, Set.of());

    /**
     * Adaptador de la fuente nueva: concatena el catálogo existente (primero) y la fuente externa (después), con la
     * misma política de búsqueda (filtros y orden) y una paginación continua sobre las dos.
     */
    static final class FuenteAdicionalAdapter implements OfertaRepositoryPort {

        private final OfertaRepositoryPort existente;
        private final List<AtractivoPublico> externos;
        private final PoliticaBusqueda politica = new PoliticaBusqueda();

        FuenteAdicionalAdapter(OfertaRepositoryPort existente, List<AtractivoPublico> externos) {
            this.existente = existente;
            this.externos = externos;
        }

        @Override
        public PaginaResultados<ResultadoOferta> buscar(CriteriosBusqueda criterios) {
            PaginaResultados<ResultadoOferta> catalogo = existente.buscar(criterios);
            List<ResultadoOferta> deLaFuente = externos.stream()
                    .map(a -> politica.evaluar(a, criterios))
                    .flatMap(Optional::stream)
                    .sorted(politica.comparador(criterios.orden()))
                    .toList();

            List<ResultadoOferta> pagina = new ArrayList<>(catalogo.elementos());
            long inicio = (long) criterios.pagina() * criterios.tamanoPagina();
            int desde = (int) Math.min(Math.max(0, inicio - catalogo.totalElementos()), deLaFuente.size());
            int hasta = Math.min(desde + criterios.tamanoPagina() - pagina.size(), deLaFuente.size());
            pagina.addAll(deLaFuente.subList(desde, Math.max(desde, hasta)));
            return new PaginaResultados<>(pagina, criterios.pagina(), criterios.tamanoPagina(),
                    catalogo.totalElementos() + deLaFuente.size());
        }
    }

    /** Lo único que cambia la configuración: el bean del puerto apunta al adaptador nuevo. */
    @TestConfiguration
    static class RegistroDeLaFuente {

        @Bean
        @Primary
        OfertaRepositoryPort ofertaConFuenteAdicional(OfertaMongoAdapter mongo) {
            return new FuenteAdicionalAdapter(mongo, List.of(ATRACTIVO_EXTERNO));
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private OfertaMongoAdapter soloMongo;

    @Test
    void laBusquedaPublicaDevuelveLosDatosDeLaFuenteNueva() throws Exception {
        String cuerpo = buscar("/api/v1/catalogo/oferta?texto=avistamiento");

        assertThat(JsonPath.<Integer>read(cuerpo, "$.totalElementos")).isEqualTo(1);
        assertThat(JsonPath.<String>read(cuerpo, "$.elementos[0].nombre")).isEqualTo(ATRACTIVO_EXTERNO.getNombre());
        assertThat(JsonPath.<String>read(cuerpo, "$.elementos[0].tipo")).isEqualTo("Atractivo");
    }

    @Test
    void elCatalogoExistenteSigueIgualYLaFuenteNuevaSeSuma() throws Exception {
        long delCatalogo = soloMongo.buscar(CriteriosBusqueda.sinFiltros().conOrden(OrdenBusqueda.Nombre)).totalElementos();
        assertThat(delCatalogo).isPositive();

        String primera = buscar("/api/v1/catalogo/oferta");
        int total = JsonPath.read(primera, "$.totalElementos");
        assertThat(total).isEqualTo(delCatalogo + 1);

        int ultimaPagina = (total - 1) / 10;
        String ultima = buscar("/api/v1/catalogo/oferta?pagina=" + ultimaPagina);
        List<String> nombres = JsonPath.read(ultima, "$.elementos[*].nombre");
        assertThat(nombres).last().isEqualTo(ATRACTIVO_EXTERNO.getNombre());
    }

    @Test
    void losFiltrosDeLaBusquedaTambienAplicanALaFuenteNueva() throws Exception {
        // Un atractivo nunca cumple una categoría de servicio: la fuente nueva no aporta resultados.
        String cuerpo = buscar("/api/v1/catalogo/oferta?texto=avistamiento&categoria=Tour");

        assertThat(JsonPath.<Integer>read(cuerpo, "$.totalElementos")).isZero();
    }

    private String buscar(String ruta) throws Exception {
        return mvc.perform(get(ruta)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
