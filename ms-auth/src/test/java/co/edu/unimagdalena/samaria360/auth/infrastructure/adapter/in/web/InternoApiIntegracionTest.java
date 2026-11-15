package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;
import co.edu.unimagdalena.samaria360.auth.infrastructure.PruebaIntegracion;
import co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.security.JwtServicioAdapter;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Endpoints internos de ms-auth (DEC-280): token de servicio con credenciales de cliente y contactos filtrados por el
 * rol que cada cliente puede ver. Usa los ids de la semilla, sin iniciar sesión con sus credenciales.
 */
@PruebaIntegracion
@TestPropertySource(properties = {
        "samaria360.clientes-servicio.ms-analytics.secreto=secreto-analytics-prueba",
        "samaria360.clientes-servicio.ms-booking.secreto=secreto-booking-prueba"})
class InternoApiIntegracionTest {

    private static final String TURISTA = "00000000-0000-4000-8000-000000000001";
    private static final String PRESTADOR = "00000000-0000-4000-8000-000000000002";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtServicioAdapter tokens;

    @Test
    void conElSecretoCorrectoEmiteUnTokenDeServicioParaMsAuth() throws Exception {
        MockHttpServletResponse respuesta = pedirToken("ms-analytics", "secreto-analytics-prueba");

        assertThat(respuesta.getStatus()).isEqualTo(200);
        String token = JsonPath.read(respuesta.getContentAsString(), "$.token");
        String[] partes = token.split("\\.");
        String carga = new String(java.util.Base64.getUrlDecoder().decode(partes[1]));
        assertThat(JsonPath.<String>read(carga, "$.sub")).isEqualTo("ms-analytics");
        assertThat(JsonPath.<String>read(carga, "$.rol")).isEqualTo("SERVICIO");
        assertThat(JsonPath.<Object>read(carga, "$.aud").toString()).contains("ms-auth").doesNotContain("ms-catalog");
    }

    /** El mismo 401 para un cliente desconocido y un secreto incorrecto. */
    @Test
    void unSecretoIncorrectoOUnClienteDesconocidoSonEl401() throws Exception {
        MockHttpServletResponse malo = pedirToken("ms-analytics", "otro");
        MockHttpServletResponse desconocido = pedirToken("ms-catalog", "secreto-analytics-prueba");

        assertThat(malo.getStatus()).isEqualTo(401);
        assertThat(desconocido.getStatus()).isEqualTo(401);
        assertThat(JsonPath.<String>read(malo.getContentAsString(), "$.codigo"))
                .isEqualTo("CREDENCIALES_SERVICIO_INVALIDAS")
                .isEqualTo(JsonPath.read(desconocido.getContentAsString(), "$.codigo"));
    }

    /** Mínimo privilegio: ms-analytics solo ve prestadores; ms-booking, solo turistas. */
    @Test
    void cadaClienteVeSoloLosContactosDeSuRol() throws Exception {
        String analytics = JsonPath.read(pedirToken("ms-analytics", "secreto-analytics-prueba").getContentAsString(),
                "$.token");
        String booking = JsonPath.read(pedirToken("ms-booking", "secreto-booking-prueba").getContentAsString(),
                "$.token");

        String paraAnalytics = contactos(analytics).getContentAsString();
        String paraBooking = contactos(booking).getContentAsString();

        assertThat(JsonPath.<List<String>>read(paraAnalytics, "$.elementos[*].idUsuario")).containsExactly(PRESTADOR);
        assertThat(JsonPath.<String>read(paraAnalytics, "$.elementos[0].correo")).isNotBlank();
        assertThat(JsonPath.<String>read(paraAnalytics, "$.elementos[0].telefono")).isNotBlank();
        assertThat(JsonPath.<List<String>>read(paraBooking, "$.elementos[*].idUsuario")).containsExactly(TURISTA);
        assertThat(JsonPath.<String>read(paraBooking, "$.elementos[0].nombre")).isNotBlank();
    }

    @Test
    void sinTokenOConElTokenDelRelevoNoHayContactos() throws Exception {
        MockHttpServletResponse sinToken = mvc.perform(post("/internal/v1/contactos")
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"" + PRESTADOR + "\"]}"))
                .andReturn().getResponse();
        // El token del relevo HTTP tiene la aud de ms-catalog: no sirve aquí.
        MockHttpServletResponse delRelevo = contactos(tokens.emitir(Destino.MS_CATALOG, Instant.now()));

        assertThat(sinToken.getStatus()).isEqualTo(401);
        assertThat(delRelevo.getStatus()).isEqualTo(401);
    }

    @Test
    void masDeCienIdsEsDatosInvalidos() throws Exception {
        String token = JsonPath.read(pedirToken("ms-analytics", "secreto-analytics-prueba").getContentAsString(),
                "$.token");
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            ids.append(i == 0 ? "" : ",").append('"').append(java.util.UUID.randomUUID()).append('"');
        }

        MockHttpServletResponse respuesta = mvc.perform(post("/internal/v1/contactos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[" + ids + "]}")).andReturn().getResponse();

        assertThat(respuesta.getStatus()).isEqualTo(400);
        assertThat(JsonPath.<String>read(respuesta.getContentAsString(), "$.campos.ids")).isEqualTo("LONGITUD_MAXIMA");
    }

    private MockHttpServletResponse pedirToken(String cliente, String secreto) throws Exception {
        return mvc.perform(post("/internal/v1/tokens").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cliente\":\"%s\",\"secreto\":\"%s\"}".formatted(cliente, secreto)))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse contactos(String token) throws Exception {
        return mvc.perform(post("/internal/v1/contactos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"%s\",\"%s\"]}".formatted(TURISTA, PRESTADOR))).andReturn().getResponse();
    }
}
