package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.catalog.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.PruebaIntegracion;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.TokensDePrueba;
import com.jayway.jsonpath.JsonPath;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.SolicitudesServicio.con;
import static co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.SolicitudesServicio.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * CU «Registrar servicio» de punta a punta contra MongoDB (RF09, RF10, RF37; DEC-124 a DEC-141): el servicio Pendiente,
 * la validación por campo, la seguridad (rol Prestador, cuenta Activa con RNT y {@code ver}) y que un Pendiente no se ve
 * en público. Cada prueba usa su propio prestador y borra lo que agregó.
 */
@PruebaIntegracion
class RegistrarServicioApiIntegracionTest {

    private static final String RUTA = "/api/v1/catalogo/prestador/servicios";
    private static final Instant AHORA = Instant.parse("2026-10-24T15:00:00.321Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private RelojFijo reloj;

    private final UUID prestador = UUID.randomUUID();
    private final UUID otro = UUID.randomUUID();
    private final String nombre = "Servicio de registro " + UUID.randomUUID();

    @BeforeEach
    void preparar() {
        reloj.ajustar(AHORA);
        filaDeCuenta(prestador, "Prestador", "Activo", 2, "RNT-REG-1");
    }

    @AfterEach
    void limpiar() {
        for (UUID id : List.of(prestador, otro)) {
            mongo.getCollection("servicios").deleteMany(Filters.eq("prestadorId", id.toString()));
            mongo.getCollection("cuentas").deleteOne(Filters.eq("_id", id.toString()));
        }
    }

    @Test
    void cadaCategoriaQuedaPendienteConElPrestadorDelTokenYLosDatosDeLaProyeccion() throws Exception {
        for (Map<String, Object> cuerpo : List.of(
                SolicitudesServicio.alojamiento(nombre, List.of(SolicitudesServicio.unidad("Doble", 6, 2, "180000"))),
                SolicitudesServicio.tour(nombre), SolicitudesServicio.gastronomia(nombre),
                SolicitudesServicio.alquiler(nombre))) {
            MvcResult resultado = registrar(cuerpo, token(prestador, 2));

            assertThat(resultado.getResponse().getStatus()).as("%s", cuerpo.get("categoria")).isEqualTo(201);
            String json = cuerpo(resultado);
            String id = JsonPath.read(json, "$.id");
            assertThat(resultado.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(RUTA + "/" + id);
            assertThat(JsonPath.<String>read(json, "$.estadoPublicacion")).isEqualTo("Pendiente");
            assertThat(JsonPath.<String>read(json, "$.idPrestador")).isEqualTo(prestador.toString());
            assertThat(JsonPath.<Boolean>read(json, "$.prestadorActivo")).isTrue();
            assertThat(JsonPath.<String>read(json, "$.rnt")).isEqualTo("RNT-REG-1");
            assertThat(JsonPath.<String>read(json, "$.fechaRegistro")).isEqualTo("2026-10-24T15:00:00.321Z");
            assertThat(JsonPath.<String>read(json, "$.direccion")).isEqualTo("Carrera 1 # 2-3, Taganga");
            assertThat(JsonPath.<Double>read(json, "$.coordenadas.longitud")).isEqualTo(-74.1906);
            assertThat(json).doesNotContain("revisadoPor", "estadoCuentaPrestador");

            Document guardado = mongo.getCollection("servicios").find(Filters.eq("_id", new ObjectId(id))).first();
            assertThat(guardado.getString("estadoPublicacion")).isEqualTo("Pendiente");
            assertThat(guardado.getString("prestadorId")).isEqualTo(prestador.toString());
            assertThat(guardado.getString("rnt")).isEqualTo("RNT-REG-1");
            assertThat(guardado.getDate("fechaRegistro")).isEqualTo(Date.from(AHORA));
        }
        Document tour = mongo.getCollection("servicios")
                .find(Filters.and(Filters.eq("prestadorId", prestador.toString()), Filters.eq("categoria", "Tour")))
                .first();
        assertThat(tour.get("oferta", Document.class)).isEqualTo(new Document("diasOperacion",
                List.of("Sabado", "Domingo")).append("salidas", List.of("08:00")));
    }

    @Test
    void unAlojamientoConVariasUnidadesDerivaElPrecioDesdeYElServidorGeneraLosIdsDeUnidad() throws Exception {
        MvcResult resultado = registrar(SolicitudesServicio.alojamiento(nombre, List.of(
                SolicitudesServicio.unidad("Suite", 2, 4, "320000"),
                SolicitudesServicio.unidad("Doble", 8, 2, "150000"))), token(prestador, 2));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
        String json = cuerpo(resultado);
        assertThat(JsonPath.<Number>read(json, "$.precioBase").doubleValue()).isEqualTo(150000);
        List<String> ids = JsonPath.read(json, "$.detalleCategoria.unidades[*].idUnidad");
        assertThat(ids).hasSize(2).doesNotHaveDuplicates().allMatch(ObjectId::isValid);
        Document guardado = mongo.getCollection("servicios")
                .find(Filters.eq("_id", new ObjectId(JsonPath.<String>read(json, "$.id")))).first();
        assertThat(guardado.get("precioBase")).isEqualTo(Decimal128.parse("150000"));
    }

    @Test
    void losCamposInvalidosSonDatosInvalidosConSusNombresJson() throws Exception {
        Map<String, Object> cuerpo = con(SolicitudesServicio.tour(nombre), c -> {
            c.put("nombreComercial", " ");
            c.put("coordenadas", Map.of("longitud", -200, "latitud", 11.2));
            c.put("contacto", Map.of("telefono", "+57 300", "correo", "sin-arroba"));
            c.put("detalleCategoria", Map.of("puntoEncuentro", "Muelle", "duracionHoras", 2, "aforoMaxGrupo", 0));
            c.put("oferta", Map.of("diasOperacion", List.of("Lunes", "Feriado"), "salidas", List.of("8:00")));
        });

        MvcResult resultado = registrar(cuerpo, token(prestador, 2));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        String json = cuerpo(resultado);
        assertThat(JsonPath.<String>read(json, "$.codigo")).isEqualTo("DATOS_INVALIDOS");
        assertThat(JsonPath.<Map<String, String>>read(json, "$.campos")).containsOnly(
                Map.entry("nombreComercial", "REQUERIDO"),
                Map.entry("coordenadas.longitud", "VALOR_MINIMO"),
                Map.entry("contacto.correo", "FORMATO"),
                Map.entry("detalleCategoria.aforoMaxGrupo", "VALOR_MINIMO"),
                Map.entry("oferta.diasOperacion[1]", "NO_PERMITIDO"),
                Map.entry("oferta.salidas[0]", "FORMATO"));
        assertThat(mongo.getCollection("servicios").countDocuments(Filters.eq("prestadorId", prestador.toString())))
                .isZero();
    }

    @Test
    void lasUnidadesInvalidasSeNombranConSuIndice() throws Exception {
        MvcResult resultado = registrar(SolicitudesServicio.alojamiento(nombre, List.of(
                SolicitudesServicio.unidad("Doble", 2, 2, "150000"),
                SolicitudesServicio.unidad("", 0, 2, "-1"))), token(prestador, 2));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonPath.<Map<String, String>>read(cuerpo(resultado), "$.campos")).containsOnly(
                Map.entry("detalleCategoria.unidades[1].nombre", "REQUERIDO"),
                Map.entry("detalleCategoria.unidades[1].numHabitaciones", "VALOR_MINIMO"),
                Map.entry("detalleCategoria.unidades[1].precioNoche", "VALOR_MINIMO"));
    }

    @Test
    void unJsonInvalidoEsDatosInvalidosSinCampos() throws Exception {
        MvcResult resultado = mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, token(prestador, 2))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombreComercial\":")).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("DATOS_INVALIDOS");
    }

    /** DEC-141: lo que fija el servidor no se toma del cuerpo. */
    @Test
    void elPrestadorElEstadoYElRntDelCuerpoSeIgnoran() throws Exception {
        Map<String, Object> cuerpo = con(SolicitudesServicio.tour(nombre), c -> {
            c.put("prestadorId", otro.toString());
            c.put("estadoPublicacion", "Publicado");
            c.put("prestadorActivo", false);
            c.put("rnt", "RNT-FALSO");
        });

        String json = cuerpo(registrar(cuerpo, token(prestador, 2)));

        assertThat(JsonPath.<String>read(json, "$.idPrestador")).isEqualTo(prestador.toString());
        assertThat(JsonPath.<String>read(json, "$.estadoPublicacion")).isEqualTo("Pendiente");
        assertThat(JsonPath.<Boolean>read(json, "$.prestadorActivo")).isTrue();
        assertThat(JsonPath.<String>read(json, "$.rnt")).isEqualTo("RNT-REG-1");
    }

    @Test
    void unServicioPendienteNoApareceEnLaBusquedaNiEnElDetallePublico() throws Exception {
        String id = JsonPath.read(cuerpo(registrar(SolicitudesServicio.tour(nombre), token(prestador, 2))), "$.id");

        MvcResult busqueda = mvc.perform(get("/api/v1/catalogo/oferta").param("texto", nombre)).andReturn();
        assertThat(JsonPath.<List<String>>read(cuerpo(busqueda), "$.elementos[*].nombre")).doesNotContain(nombre);
        MvcResult detalle = mvc.perform(get("/api/v1/catalogo/servicios/" + id)).andReturn();
        assertThat(detalle.getResponse().getStatus()).isEqualTo(404);
        assertThat(JsonPath.<String>read(cuerpo(detalle), "$.codigo")).isEqualTo("OFERTA_NO_DISPONIBLE");
    }

    // ---- seguridad (RNF06, DEC-125, DEC-139) -----------------------------------------------------------------

    @Test
    void sinTokenEs401() throws Exception {
        for (MockHttpServletRequestBuilder solicitud : List.of(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                .content(json(SolicitudesServicio.tour(nombre))), get(RUTA), get(RUTA + "/" + new ObjectId()))) {
            MvcResult resultado = mvc.perform(solicitud).andReturn();
            assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
            assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("NO_AUTENTICADO");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Turista", "Gestor", "Administrador"})
    void otroRolEs403AccesoDenegado(String rol) throws Exception {
        filaDeCuenta(otro, rol, "Activo", 1, null);
        String token = TokensDePrueba.bearer(TokensDePrueba.usuario(otro, rol, 1));

        for (MockHttpServletRequestBuilder solicitud : List.of(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                .content(json(SolicitudesServicio.tour(nombre))), get(RUTA))) {
            MvcResult resultado = mvc.perform(solicitud.header(HttpHeaders.AUTHORIZATION, token)).andReturn();
            assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
            assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("ACCESO_DENEGADO");
        }
    }

    @Test
    void unPrestadorSinFilaEnLaProyeccionEs403PrestadorNoHabilitado() throws Exception {
        MvcResult resultado = registrar(SolicitudesServicio.tour(nombre),
                TokensDePrueba.bearer(TokensDePrueba.usuario(otro, "Prestador", 0)));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("PRESTADOR_NO_HABILITADO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pendiente", "Rechazado", "Inactivo"})
    void unPrestadorQueNoEstaActivoEs403PrestadorNoHabilitado(String estado) throws Exception {
        filaDeCuenta(otro, "Prestador", estado, 1, "RNT-OTRO");

        MvcResult resultado = registrar(SolicitudesServicio.tour(nombre), token(otro, 1));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("PRESTADOR_NO_HABILITADO");
        assertThat(mongo.getCollection("servicios").countDocuments(Filters.eq("prestadorId", otro.toString())))
                .isZero();
    }

    /** Una proyección anterior a V17 de ms-auth no tiene RNT (DEC-126, DEC-139). */
    @Test
    void unPrestadorActivoSinRntEnLaProyeccionEs403() throws Exception {
        filaDeCuenta(otro, "Prestador", "Activo", 1, null);

        MvcResult resultado = registrar(SolicitudesServicio.tour(nombre), token(otro, 1));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("PRESTADOR_NO_HABILITADO");
    }

    @Test
    void unTokenConVerViejoEs401SesionInvalidada() throws Exception {
        MvcResult resultado = registrar(SolicitudesServicio.tour(nombre), token(prestador, 1));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("SESION_INVALIDADA");
    }

    // ---- detalle público (DEC-150, HC2) ----------------------------------------------------------------------

    @Test
    void elDetallePublicoTraeLaDireccionElRntYLaOfertaDeLaSemilla() throws Exception {
        String hotel = cuerpo(mvc.perform(get("/api/v1/catalogo/servicios/" + idDe("Hotel Casa Tayrona")))
                .andReturn());
        String tour = cuerpo(mvc.perform(get("/api/v1/catalogo/servicios/" + idDe("Tour de Café en Minca")))
                .andReturn());
        String restaurante = cuerpo(mvc.perform(get("/api/v1/catalogo/servicios/"
                + idDe("Restaurante Ouzo Santa Marta"))).andReturn());

        assertThat(JsonPath.<String>read(hotel, "$.direccion")).isNotBlank();
        assertThat(JsonPath.<String>read(hotel, "$.rnt")).isEqualTo("RNT-00000");
        assertThat(JsonPath.<Map<String, Object>>read(hotel, "$.oferta"))
                .containsOnly(Map.entry("diasOperacion", SolicitudesServicio.TODOS_LOS_DIAS));
        assertThat(JsonPath.<List<String>>read(tour, "$.oferta.salidas")).containsExactly("08:00", "14:00");
        assertThat(JsonPath.<Object>read(restaurante, "$.oferta")).isNull();
    }

    // ---- auxiliares ------------------------------------------------------------------------------------------

    /** DEC-318: multipart con la parte datos y las fotos; las imágenes quedan servidas por la ruta pública. */
    @Test
    void elRegistroConFotosGuardaLasImagenesYLasSirve() throws Exception {
        byte[] jpeg = SolicitudesAtractivo.imagen(SolicitudesAtractivo.FIRMA_JPEG, 2048);
        MvcResult resultado = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart(RUTA)
                .file(SolicitudesAtractivo.parteDatos(SolicitudesServicio.tour(nombre)))
                .file(SolicitudesAtractivo.parteImagen("portada.jpg", jpeg))
                .header(HttpHeaders.AUTHORIZATION, token(prestador, 2))).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(201);
        List<String> imagenes = JsonPath.read(cuerpo(resultado), "$.imagenes");
        assertThat(imagenes).hasSize(1).allMatch(ruta -> ruta.startsWith("/api/v1/catalogo/imagenes/"));
        MvcResult imagen = mvc.perform(get(imagenes.get(0))).andReturn();
        assertThat(imagen.getResponse().getStatus()).isEqualTo(200);
        assertThat(imagen.getResponse().getContentAsByteArray()).isEqualTo(jpeg);
        mongo.getCollection("imagenes.files").deleteOne(Filters.eq("_id",
                new ObjectId(imagenes.get(0).substring("/api/v1/catalogo/imagenes/".length()))));
    }

    @Test
    void elRegistroMultipartSinFotosEs400ConImagenesRequerido() throws Exception {
        MvcResult resultado = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart(RUTA)
                .file(SolicitudesAtractivo.parteDatos(SolicitudesServicio.tour(nombre)))
                .header(HttpHeaders.AUTHORIZATION, token(prestador, 2))).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.campos.imagenes")).isEqualTo("REQUERIDO");
        assertThat(mongo.getCollection("servicios").countDocuments(Filters.eq("prestadorId", prestador.toString())))
                .isZero();
    }

    private MvcResult registrar(Map<String, Object> cuerpo, String token) throws Exception {
        return mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo))).andReturn();
    }

    private static String token(UUID id, long ver) {
        return TokensDePrueba.bearer(TokensDePrueba.usuario(id, "Prestador", ver));
    }

    private void filaDeCuenta(UUID id, String rol, String estado, long version, String rnt) {
        Document cuenta = new Document("_id", id.toString()).append("rol", rol).append("estadoCuenta", estado)
                .append("version", version).append("actualizadoEn", new Date());
        if (rnt != null) {
            cuenta.append("rnt", rnt);
        }
        mongo.getCollection("cuentas").insertOne(cuenta);
    }

    private String idDe(String nombreComercial) {
        return mongo.getCollection("servicios").find(Filters.eq("nombreComercial", nombreComercial)).first()
                .getObjectId("_id").toHexString();
    }

    private static String cuerpo(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
