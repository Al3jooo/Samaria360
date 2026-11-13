package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.catalog.infrastructure.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * GET /api/v1/catalogo/oferta contra MongoDB real con la semilla de infra/mongo/init: una prueba por cada decisión
 * de docs/trazabilidad.md, más distancia, paginación, texto y errores.
 * Semilla: 11 servicios visibles (de 14) y 7 atractivos.
 */
@PruebaIntegracion
class OfertaApiIntegracionTest {

    private static final String OFERTA = "/api/v1/catalogo/oferta";
    private static final String PARQUE_DE_LOS_NOVIOS = "lat=11.2420&lng=-74.2117";

    @Autowired
    private MockMvc mvc;

    // ------------------------------------------------------------------ decisiones de búsqueda

    @Test
    void decision1_losAtractivosAparecenJuntoConLosServicios() throws Exception {
        List<Map<String, Object>> todos = todos("");

        assertThat(todos).hasSize(18);
        assertThat(todos).filteredOn(e -> "Servicio".equals(e.get("tipo"))).hasSize(11);
        assertThat(todos).filteredOn(e -> "Atractivo".equals(e.get("tipo"))).hasSize(7);
    }

    @Test
    void losServiciosPendientesYDePrestadoresInactivosNuncaAparecen() throws Exception {
        assertThat(nombres(todos(""))).doesNotContain(
                "Cocina Caribe El Rodadero", "Alquiler de kayaks Taganga", "Snorkel en Bahía Concha");
    }

    @Test
    void decision2_conCategoriaLosAtractivosQuedanFuera() throws Exception {
        List<Map<String, Object>> tours = todos("categoria=Tour");

        assertThat(nombres(tours)).containsExactlyInAnyOrder(
                "Tour de Café en Minca", "Tour de café Samaria Alta", "Caminata a Pueblito Tayrona");
        assertThat(tours).extracting(e -> e.get("tipo")).containsOnly("Servicio");
    }

    @Test
    void decision3_elPrecioMaximoExcluyeLosAtractivos() throws Exception {
        List<Map<String, Object>> baratos = todos("precioMax=90000");

        assertThat(nombres(baratos)).containsExactlyInAnyOrder("Hostal Bahía Taganga", "Restaurante Ouzo Santa Marta",
                "Mercado Gastronómico La Plaza", "Mirador Gourmet Taganga", "Tour de Café en Minca",
                "Bicicletas Centro Histórico");
        assertThat(baratos).extracting(e -> e.get("tipo")).containsOnly("Servicio");
        assertThat(baratos).allSatisfy(e -> assertThat(((Number) e.get("precio")).doubleValue()).isLessThanOrEqualTo(90000));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PrecioAscendente", "PrecioDescendente"})
    void decision4_alOrdenarPorPrecioLosResultadosSinPrecioVanAlFinal(String orden) throws Exception {
        List<Map<String, Object>> todos = todos("orden=" + orden);
        List<Double> precios = todos.subList(0, 11).stream().map(e -> ((Number) e.get("precio")).doubleValue()).toList();

        assertThat(todos.subList(0, 11)).extracting(e -> e.get("tipo")).containsOnly("Servicio");
        assertThat(todos.subList(11, 18)).allSatisfy(e -> {
            assertThat(e.get("tipo")).isEqualTo("Atractivo");
            assertThat(e.get("precio")).isNull();
        });
        if (orden.equals("PrecioAscendente")) {
            assertThat(precios).isSorted();
        } else {
            assertThat(precios).isSortedAccordingTo((a, b) -> Double.compare(b, a));
        }
    }

    @Test
    void decision5_losAtractivosCerradosOEnMantenimientoAparecenConSuEstado() throws Exception {
        Map<String, Object> cascada = porNombre(todos(""), "Cascada El Salto");
        Map<String, Object> mirador = porNombre(todos(""), "Mirador de Taganga");

        assertThat(cascada.get("estadoOperativo")).isEqualTo("Cerrado_temporalmente");
        assertThat(mirador.get("estadoOperativo")).isEqualTo("En_mantenimiento");
        assertThat(porNombre(todos(""), "Hotel Casa Tayrona").get("estadoOperativo")).isNull();
    }

    @Test
    void decision6_elFiltroDeAccesibilidadAplicaAAmbos() throws Exception {
        List<Map<String, Object>> accesibles = todos("accesibilidad=MovilidadReducida");

        assertThat(nombres(accesibles)).containsExactlyInAnyOrder("Hotel Casa Tayrona", "Restaurante Ouzo Santa Marta",
                "Mercado Gastronómico La Plaza", "Quinta de San Pedro Alejandrino", "Catedral Basílica de Santa Marta");
        assertThat(todos("accesibilidad=MovilidadReducida&accesibilidad=Visual"))
                .extracting(e -> e.get("nombre")).containsExactly("Quinta de San Pedro Alejandrino");
    }

    // ------------------------------------------------------------------ distancia y orden

    @Test
    void ordenaPorDistanciaDesdeElParqueDeLosNovios() throws Exception {
        List<Map<String, Object>> todos = todos(PARQUE_DE_LOS_NOVIOS);
        List<Double> distancias = todos.stream().map(e -> ((Number) e.get("distanciaKm")).doubleValue()).toList();

        assertThat(distancias).isSorted();
        assertThat(nombres(todos.subList(0, 5))).containsExactlyInAnyOrder("Hotel Casa Tayrona",
                "Restaurante Ouzo Santa Marta", "Bicicletas Centro Histórico", "Catedral Basílica de Santa Marta",
                "Mercado Gastronómico La Plaza");
        assertThat(distancias.get(0)).isLessThan(0.5);
    }

    @Test
    void elFiltroDeDistanciaDejaSoloLoCercano() throws Exception {
        List<Map<String, Object>> cercanos = todos(PARQUE_DE_LOS_NOVIOS + "&distanciaKm=2");

        assertThat(nombres(cercanos)).containsExactlyInAnyOrder("Hotel Casa Tayrona", "Restaurante Ouzo Santa Marta",
                "Bicicletas Centro Histórico", "Catedral Basílica de Santa Marta", "Mercado Gastronómico La Plaza");
        assertThat(cercanos).allSatisfy(e -> assertThat(((Number) e.get("distanciaKm")).doubleValue()).isLessThanOrEqualTo(2.0));
    }

    @Test
    void sinUbicacionNoHayDistanciaYElOrdenEsPorNombre() throws Exception {
        List<Map<String, Object>> todos = todos("");

        assertThat(todos).allSatisfy(e -> assertThat(e.get("distanciaKm")).isNull());
        // La colación «es» de MongoDB ordena igual que el Collator español de PoliticaBusqueda.
        Collator espanol = Collator.getInstance(Locale.forLanguageTag("es"));
        espanol.setStrength(Collator.PRIMARY);
        assertThat(nombres(todos)).isSortedAccordingTo(espanol::compare);
        assertThat(nombres(todos).subList(0, 3))
                .containsExactly("Bicicletas Centro Histórico", "Cabañas El Roble", "Cabo San Juan del Guía");
    }

    @Test
    void laCoordenadaLlegaConLongitudYLatitudExplicitas() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> coordenada = (Map<String, Object>) porNombre(todos(""), "Hotel Casa Tayrona").get("coordenada");

        assertThat(coordenada).containsEntry("longitud", -74.2125).containsEntry("latitud", 11.2425);
    }

    // ------------------------------------------------------------------ paginación

    @Test
    void laPaginacionNoRepiteNiOmiteElementos() throws Exception {
        String primera = cuerpo(buscar("tamano=4&pagina=0"));
        int totalPaginas = JsonPath.read(primera, "$.totalPaginas");
        assertThat(totalPaginas).isEqualTo(5);
        assertThat((Integer) JsonPath.read(primera, "$.totalElementos")).isEqualTo(18);

        List<String> ids = new ArrayList<>();
        for (int pagina = 0; pagina < totalPaginas; pagina++) {
            List<String> idsPagina = JsonPath.read(cuerpo(buscar("tamano=4&pagina=" + pagina)), "$.elementos[*].id");
            assertThat(idsPagina).hasSizeLessThanOrEqualTo(4);
            ids.addAll(idsPagina);
        }

        assertThat(ids).hasSize(18).doesNotHaveDuplicates();
        assertThat(new HashSet<>(ids)).isEqualTo(new HashSet<>(todos("").stream().map(e -> (String) e.get("id")).toList()));
        assertThat((List<?>) JsonPath.read(cuerpo(buscar("tamano=4&pagina=5")), "$.elementos")).isEmpty();
    }

    // ------------------------------------------------------------------ texto

    @Test
    void unTextoConMetacaracteresDeRegexSeTrataComoLiteral() throws Exception {
        // Sin escapar, ".*" coincidiría con los 18.
        assertThat((Integer) JsonPath.read(cuerpo(buscar("texto=.*")), "$.totalElementos")).isZero();
        MvcResult parentesis = buscar("texto=(");
        assertThat(parentesis.getResponse().getStatus()).isEqualTo(200);
        assertThat((Integer) JsonPath.read(cuerpo(parentesis), "$.totalElementos")).isZero();
    }

    @Test
    void elTextoBuscaEnNombreYDescripcionSinDistinguirMayusculas() throws Exception {
        // «Cabañas El Roble» dice «cafetales» en su descripción: desde DEC-274, «CAFÉ» también coincide sin la tilde.
        assertThat(nombres(todos("texto=CAFÉ")))
                .containsExactlyInAnyOrder("Cabañas El Roble", "Tour de Café en Minca", "Tour de café Samaria Alta");
        assertThat(nombres(todos("texto=piscinas"))).containsExactly("Cascada Quebrada Valencia");
    }

    @Test
    void elTextoNoDistingueTildesEnNingunSentido() throws Exception {
        // DEC-274: «basilica» encuentra «Basílica» y «CAFE» encuentra «Café» (antes no).
        assertThat(nombres(todos("texto=BASÍLICA"))).containsExactly("Catedral Basílica de Santa Marta");
        assertThat(nombres(todos("texto=basilica"))).containsExactly("Catedral Basílica de Santa Marta");
        assertThat(nombres(todos("texto=CAFE")))
                .containsExactlyInAnyOrder("Cabañas El Roble", "Tour de Café en Minca", "Tour de café Samaria Alta");
    }

    // ------------------------------------------------------------------ errores

    @ParameterizedTest
    @ValueSource(strings = {
            "tamano=11",
            "tamano=0",
            "pagina=-1",
            "distanciaKm=5",
            "orden=Distancia",
            "precioMax=-1",
            "precioMax=abc",
            "lat=11.24",
            "lng=-74.21",
            "lat=200&lng=-74.21",
            "categoria=Inexistente",
            "orden=Calificacion",
            "accesibilidad=Rampa"
    })
    void criteriosInvalidosResponden400(String parametros) throws Exception {
        MvcResult resultado = buscar(parametros);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(resultado.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat((String) JsonPath.read(cuerpo(resultado), "$.codigo")).isEqualTo("CRITERIOS_INVALIDOS");
    }

    @Test
    void unTextoDeMasDe100CaracteresResponde400() throws Exception {
        MvcResult resultado = buscar("texto=" + "a".repeat(101));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat((String) JsonPath.read(cuerpo(resultado), "$.codigo")).isEqualTo("CRITERIOS_INVALIDOS");
        assertThat(buscar("texto=" + "a".repeat(100)).getResponse().getStatus()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ seguridad

    @Test
    void laBusquedaEsPublicaYLoDemasExigeToken() throws Exception {
        assertThat(buscar("").getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus()).isEqualTo(200);

        MvcResult protegido = mvc.perform(get("/api/v1/catalogo/otra-ruta")).andReturn();
        assertThat(protegido.getResponse().getStatus()).isEqualTo(401);
        assertThat((String) JsonPath.read(cuerpo(protegido), "$.codigo")).isEqualTo("NO_AUTENTICADO");
    }

    // ------------------------------------------------------------------ auxiliares

    private MvcResult buscar(String parametros) throws Exception {
        return mvc.perform(get(OFERTA + (parametros.isEmpty() ? "" : "?" + parametros))).andReturn();
    }

    /** Recorre todas las páginas de 10 y devuelve los elementos en orden. */
    private List<Map<String, Object>> todos(String parametros) throws Exception {
        List<Map<String, Object>> elementos = new ArrayList<>();
        String separador = parametros.isEmpty() ? "" : "&";
        for (int pagina = 0; ; pagina++) {
            MvcResult resultado = buscar(parametros + separador + "pagina=" + pagina);
            assertThat(resultado.getResponse().getStatus()).as(cuerpo(resultado)).isEqualTo(200);
            List<Map<String, Object>> pagin = JsonPath.read(cuerpo(resultado), "$.elementos");
            elementos.addAll(pagin);
            int totalPaginas = JsonPath.read(cuerpo(resultado), "$.totalPaginas");
            if (pagina + 1 >= totalPaginas) {
                return elementos;
            }
        }
    }

    private static List<String> nombres(List<Map<String, Object>> elementos) {
        return elementos.stream().map(e -> (String) e.get("nombre")).toList();
    }

    private static Map<String, Object> porNombre(List<Map<String, Object>> elementos, String nombre) {
        return elementos.stream().filter(e -> nombre.equals(e.get("nombre"))).findFirst()
                .orElseThrow(() -> new AssertionError("No aparece " + nombre));
    }

    private static String cuerpo(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
