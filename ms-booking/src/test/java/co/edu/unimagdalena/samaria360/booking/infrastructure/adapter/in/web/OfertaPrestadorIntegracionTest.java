package co.edu.unimagdalena.samaria360.booking.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.booking.infrastructure.AuthSimulado;
import co.edu.unimagdalena.samaria360.booking.infrastructure.CatalogoSimulado;
import co.edu.unimagdalena.samaria360.booking.infrastructure.PruebaIntegracion;
import co.edu.unimagdalena.samaria360.booking.infrastructure.RelojAjustable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static co.edu.unimagdalena.samaria360.booking.infrastructure.RelojAjustable.HOY;
import static co.edu.unimagdalena.samaria360.booking.infrastructure.TokensDePrueba.bearer;
import static co.edu.unimagdalena.samaria360.booking.infrastructure.TokensDePrueba.prestador;
import static co.edu.unimagdalena.samaria360.booking.infrastructure.TokensDePrueba.turista;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * RF19 y RF42 de punta a punta contra PostgreSQL (DEC-310, DEC-312): el Prestador ve y modifica el inventario de su
 * servicio con las reglas de la decisión 3 del equipo, el cambio se refleja en la disponibilidad pública y queda en el
 * outbox como {@code OfertaModificada}; el listado de asistentes trae el contacto de ms-auth.
 */
@PruebaIntegracion
class OfertaPrestadorIntegracionTest {

    private static final LocalDate MANANA = HOY.plusDays(1);
    private static final UUID PRESTADOR = CatalogoSimulado.PRESTADOR;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CatalogoSimulado catalogo;
    @Autowired
    private RelojAjustable reloj;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    private String tour;

    @BeforeEach
    void preparar() {
        reloj.reiniciar();
        AuthSimulado.CAIDO.set(false);
        tour = catalogo.publicarTour("Tour del Prestador", 10, "50000", CatalogoSimulado.TODOS_LOS_DIAS,
                List.of("08:00"));
    }

    @AfterEach
    void limpiar() {
        AuthSimulado.CAIDO.set(false);
    }

    private MvcResult hacer(MockHttpServletRequestBuilder peticion, UUID quien, String rol, Object cuerpo)
            throws Exception {
        peticion.header(HttpHeaders.AUTHORIZATION, bearer("Turista".equals(rol) ? turista(quien) : prestador(quien)));
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        }
        return mvc.perform(peticion).andReturn();
    }

    private JsonNode cuerpo(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private String ruta() {
        return "/api/v1/reservas/prestador/servicios/" + tour + "/inventario";
    }

    private void reservar(UUID quien, int personas) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("idServicio", tour);
        cuerpo.put("fechaEntrada", MANANA.toString());
        cuerpo.put("horaInicio", "08:00");
        cuerpo.put("personas", personas);
        cuerpo.put("precioTotalEsperado", new BigDecimal("50000").multiply(BigDecimal.valueOf(personas)));
        MvcResult r = hacer(post("/api/v1/reservas").header("Idempotency-Key", UUID.randomUUID().toString()), quien,
                "Turista", cuerpo);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
    }

    private int disponibles(String hora) throws Exception {
        JsonNode d = json.readTree(mvc.perform(get("/api/v1/reservas/disponibilidad").param("servicio", tour)
                .param("desde", MANANA.toString()).param("hasta", MANANA.toString())).andReturn().getResponse()
                .getContentAsString());
        for (JsonNode h : d.path("horarios")) {
            if (h.path("hora").asText().startsWith(hora)) {
                return h.path("cuposDisponibles").asInt();
            }
        }
        return -1;
    }

    @Test
    void elPrestadorCambiaCuposAbreYCierraConLasReglasDelEquipo() throws Exception {
        UUID ana = UUID.randomUUID();

        JsonNode inventario = cuerpo(hacer(get(ruta()).param("desde", MANANA.toString())
                .param("hasta", MANANA.toString()), PRESTADOR, "Prestador", null));
        assertThat(inventario.path("filas").get(0).path("cuposTotales").asInt()).isEqualTo(10);
        assertThat(inventario.path("salidas").get(0).asText()).isEqualTo("08:00");

        reservar(ana, 3);
        // Los cupos no bajan de lo reservado (409) y sí a partir de ahí; la disponibilidad pública lo refleja.
        MvcResult bajo = hacer(put(ruta()), PRESTADOR, "Prestador",
                Map.of("fecha", MANANA.toString(), "horaInicio", "08:00", "cuposTotales", 2));
        assertThat(bajo.getResponse().getStatus()).isEqualTo(409);
        assertThat(cuerpo(bajo).path("codigo").asText()).isEqualTo("CUPOS_RESERVADOS");
        assertThat(cuerpo(bajo).path("reservados").asInt()).isEqualTo(3);
        JsonNode ajustada = cuerpo(hacer(put(ruta()), PRESTADOR, "Prestador",
                Map.of("fecha", MANANA.toString(), "horaInicio", "08:00", "cuposTotales", 5)));
        assertThat(ajustada.path("cuposDisponibles").asInt()).isEqualTo(2);
        assertThat(disponibles("08:00")).isEqualTo(2);

        // Una salida nueva se abre; una con reservas no se cierra; una sin reservas sí, y desaparece de la oferta.
        assertThat(hacer(put(ruta()), PRESTADOR, "Prestador",
                Map.of("fecha", MANANA.toString(), "horaInicio", "15:30", "cuposTotales", 4)).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(disponibles("15:30")).isEqualTo(4);
        MvcResult conReservas = hacer(delete(ruta()).param("fecha", MANANA.toString()).param("horaInicio", "08:00"),
                PRESTADOR, "Prestador", null);
        assertThat(cuerpo(conReservas).path("codigo").asText()).isEqualTo("FECHA_CON_RESERVAS");
        JsonNode cerrada = cuerpo(hacer(delete(ruta()).param("fecha", MANANA.toString()).param("horaInicio", "15:30"),
                PRESTADOR, "Prestador", null));
        assertThat(cerrada.path("cerrado").asBoolean()).isTrue();
        assertThat(disponibles("15:30")).isEqualTo(-1);

        // Solo fechas futuras; un turista, 403.
        assertThat(cuerpo(hacer(put(ruta()), PRESTADOR, "Prestador",
                Map.of("fecha", HOY.toString(), "horaInicio", "08:00", "cuposTotales", 5))).path("campos").path("fecha")
                .asText()).isEqualTo("FECHA_PASADA");
        // Un servicio ajeno responde como uno que no existe (409 OFERTA_NO_DISPONIBLE, el código de ms-booking).
        assertThat(cuerpo(hacer(get(ruta()), UUID.randomUUID(), "Prestador", null)).path("codigo").asText())
                .isEqualTo("OFERTA_NO_DISPONIBLE");
        assertThat(hacer(get(ruta()), ana, "Turista", null).getResponse().getStatus()).isEqualTo(403);

        // Cada cambio quedó como OfertaModificada en el outbox, con la clave del servicio.
        List<String> tipos = jdbc.queryForList("SELECT carga->>'tipo' FROM outbox_evento WHERE tipo = 'OfertaModificada'"
                + " AND clave = ? ORDER BY id_evento", String.class, tour);
        assertThat(tipos).containsExactly("Cupos", "Apertura", "Cierre");
    }

    @Test
    void laPlantillaSeValidaYSeEncola() throws Exception {
        String plantilla = "/api/v1/reservas/prestador/servicios/" + tour + "/plantilla";
        assertThat(hacer(put(plantilla), PRESTADOR, "Prestador",
                Map.of("diasOperacion", List.of("Sabado", "Domingo"), "salidas", List.of("09:00", "07:00")))
                .getResponse().getStatus()).isEqualTo(202);
        assertThat(cuerpo(hacer(put(plantilla), PRESTADOR, "Prestador",
                Map.of("diasOperacion", List.of("Feriado"), "salidas", List.of("25:00")))).path("campos").path("salidas")
                .asText()).isEqualTo("FORMATO");

        String carga = jdbc.queryForObject("SELECT carga::text FROM outbox_evento WHERE tipo = 'OfertaModificada'"
                + " AND clave = ? AND carga->>'tipo' = 'Plantilla'", String.class, tour);
        JsonNode datos = json.readTree(carga);
        assertThat(datos.path("diasOperacion").toString()).isEqualTo("[\"Sabado\",\"Domingo\"]");
        assertThat(datos.path("salidas").toString()).isEqualTo("[\"07:00\",\"09:00\"]");
        assertThat(datos.path("valorAnterior").asText()).contains("Lunes").contains("08:00");
        // El texto del historial lleva tildes; los días del contrato, no.
        assertThat(datos.path("valorNuevo").asText()).isEqualTo("Sábado, Domingo · 07:00, 09:00");
    }

    /** Guardar la misma plantilla que ya tiene el servicio no encola nada (ni entrada en el historial de RF41). */
    @Test
    void laPlantillaSinCambiosNoSeEncola() throws Exception {
        String plantilla = "/api/v1/reservas/prestador/servicios/" + tour + "/plantilla";
        Integer antes = jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo = 'OfertaModificada'"
                + " AND clave = ?", Integer.class, tour);
        JsonNode inventario = cuerpo(hacer(get("/api/v1/reservas/prestador/servicios/" + tour + "/inventario"),
                PRESTADOR, "Prestador", null));
        List<String> dias = new java.util.ArrayList<>();
        inventario.path("diasOperacion").forEach(d -> dias.add(d.asText()));
        List<String> salidas = new java.util.ArrayList<>();
        inventario.path("salidas").forEach(h -> salidas.add(h.asText().substring(0, 5)));

        assertThat(hacer(put(plantilla), PRESTADOR, "Prestador", Map.of("diasOperacion", dias, "salidas", salidas))
                .getResponse().getStatus()).isEqualTo(202);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo = 'OfertaModificada'"
                + " AND clave = ?", Integer.class, tour)).isEqualTo(antes);
    }

    @Test
    void elListadoDeAsistentesTraeElContactoDeMsAuth() throws Exception {
        UUID ana = UUID.randomUUID();
        UUID luis = UUID.randomUUID();
        AuthSimulado.registrar(ana, "Ana Gómez", "ana@samaria360.test");
        AuthSimulado.registrar(luis, "Luis Pérez", "luis@samaria360.test");
        reservar(ana, 2);
        reservar(luis, 1);

        JsonNode listado = cuerpo(hacer(get("/api/v1/reservas/prestador/asistentes").param("servicio", tour)
                .param("fecha", MANANA.toString()), PRESTADOR, "Prestador", null));
        assertThat(listado.path("totalPersonas").asInt()).isEqualTo(3);
        assertThat(listado.path("asistentes").findValuesAsText("nombre")).containsExactly("Ana Gómez", "Luis Pérez");
        assertThat(listado.path("asistentes").get(0).path("correo").asText()).isEqualTo("ana@samaria360.test");

        // Otro prestador no ve nada; con ms-auth caído, 503.
        assertThat(cuerpo(hacer(get("/api/v1/reservas/prestador/asistentes").param("servicio", tour)
                .param("fecha", MANANA.toString()), UUID.randomUUID(), "Prestador", null)).path("asistentes").size())
                .isZero();
        AuthSimulado.CAIDO.set(true);
        MvcResult caido = hacer(get("/api/v1/reservas/prestador/asistentes").param("servicio", tour)
                .param("fecha", MANANA.toString()), PRESTADOR, "Prestador", null);
        assertThat(caido.getResponse().getStatus()).isEqualTo(503);
        assertThat(cuerpo(caido).path("servicio").asText()).isEqualTo("ms-auth");
    }
}
