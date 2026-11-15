package co.edu.unimagdalena.samaria360.chatbot.application;

import co.edu.unimagdalena.samaria360.chatbot.application.port.in.ConversarUseCase.ComandoMensaje;
import co.edu.unimagdalena.samaria360.chatbot.application.port.in.ConversarUseCase.RespuestaAsistente;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.CatalogoNoDisponibleException;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.CatalogoPort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.ConocimientoRepositoryPort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.ModeloLenguajePort.ContextoRespuesta;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.UsoDiarioPort;
import co.edu.unimagdalena.samaria360.chatbot.application.service.ConversarService;
import co.edu.unimagdalena.samaria360.chatbot.domain.exception.DatosInvalidosException;
import co.edu.unimagdalena.samaria360.chatbot.domain.exception.LimiteSuperadoException;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.EntradaConocimiento;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Idioma;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.ItemCatalogo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF04, RF36 y RF59 con dobles: catálogo, base de conocimientos, uso diario y un redactor que guarda el contexto. */
class ConversarServiceTest {

    /** 03:00 UTC del 2 de marzo es todavía el 1 de marzo en Santa Marta: el día del límite es el local. */
    private static final Instant AHORA = Instant.parse("2030-03-02T03:00:00Z");
    private static final UUID TURISTA = UUID.randomUUID();

    private final Map<String, Integer> uso = new HashMap<>();
    private final List<String> consultas = new ArrayList<>();
    private final List<ContextoRespuesta> contextos = new ArrayList<>();
    private boolean catalogoCaido;
    private List<ItemCatalogo> resultados = List.of(
            new ItemCatalogo("Atractivo", "6a0000000000000000000001", "Playa Cristal", "Naturaleza", "", "Abierto", null),
            new ItemCatalogo("Servicio", "6a0000000000000000000002", "Tour Tayrona", "Tour", "", null, 4.8));

    private final UsoDiarioPort usoDiario = (turista, dia, maximo) -> {
        String clave = turista + "|" + dia;
        int actual = uso.getOrDefault(clave, 0);
        if (actual >= maximo) {
            return OptionalLong.empty();
        }
        uso.put(clave, actual + 1);
        return OptionalLong.of(actual + 1);
    };
    private final CatalogoPort catalogo = (texto, categoria, tamano) -> {
        consultas.add(texto + "|" + categoria);
        if (catalogoCaido) {
            throw new CatalogoNoDisponibleException("caído", null);
        }
        return resultados;
    };
    private final ConocimientoRepositoryPort conocimiento = new ConocimientoRepositoryPort() {
        @Override
        public List<EntradaConocimiento> relevantes(List<String> palabras, int limite) {
            return palabras.contains("Naturaleza")
                    ? List.of(new EntradaConocimiento("k1", "Lleva protector solar.", "Clima", AHORA)) : List.of();
        }

        @Override
        public List<EntradaConocimiento> listar(int pagina, int tamano) {
            return List.of();
        }

        @Override
        public long contar() {
            return 0;
        }

        @Override
        public Optional<EntradaConocimiento> buscar(String id) {
            return Optional.empty();
        }

        @Override
        public EntradaConocimiento guardar(EntradaConocimiento entrada) {
            return entrada;
        }

        @Override
        public boolean eliminar(String id) {
            return false;
        }
    };
    private final ConversarService servicio = new ConversarService(usoDiario, catalogo, conocimiento, contexto -> {
        contextos.add(contexto);
        return "respuesta";
    }, () -> AHORA);

    @Test
    void unItinerarioUsaElCatalogoYLaBaseDeConocimientosYJustificaCadaSugerencia() {
        RespuestaAsistente respuesta = servicio.conversar(new ComandoMensaje(TURISTA,
                "Plan de 2 days para disfrutar la naturaleza", "en-US", List.of("Naturaleza")));

        // «naturaleza» es una categoría de atractivo: el tour no entra.
        assertThat(respuesta.sugerencias()).hasSize(1);
        assertThat(respuesta.sugerencias().get(0).item().nombre()).isEqualTo("Playa Cristal");
        assertThat(respuesta.sugerencias().get(0).criterio().name()).isEqualTo("PREFERENCIAS");
        assertThat(respuesta.itinerario()).hasSize(1);
        assertThat(respuesta.fuentes()).containsExactly("Clima");
        assertThat(respuesta.mensajesRestantes()).isEqualTo(19);
        ContextoRespuesta contexto = contextos.get(0);
        assertThat(contexto.idioma()).isEqualTo(Idioma.en);
        // Al modelo no le llega nada personal: ni el id del turista ni sus intereses.
        assertThat(contexto.toString()).doesNotContain(TURISTA.toString());
    }

    @Test
    void conElCatalogoCaidoRespondeConLaBaseDeConocimientosYLoDice() {
        catalogoCaido = true;

        RespuestaAsistente respuesta = servicio.conversar(new ComandoMensaje(TURISTA, "playas", "es", null));

        assertThat(respuesta.catalogoDisponible()).isFalse();
        assertThat(respuesta.sugerencias()).isEmpty();
        assertThat(contextos.get(0).catalogoDisponible()).isFalse();
    }

    /** Decisión 5 del equipo: 20 mensajes por turista al día, el día de Santa Marta. */
    @Test
    void elMensajeVeintiunoDelDiaEsElLimite() {
        for (int i = 0; i < 20; i++) {
            servicio.conversar(new ComandoMensaje(TURISTA, "hola", "es", null));
        }

        assertThatThrownBy(() -> servicio.conversar(new ComandoMensaje(TURISTA, "hola", "es", null)))
                .isInstanceOfSatisfying(LimiteSuperadoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("LIMITE_MENSAJES"));
        assertThat(uso).containsKey(TURISTA + "|" + LocalDate.of(2030, 3, 1));
    }

    @Test
    void sinPalabrasDeBusquedaNoConsultaElCatalogo() {
        servicio.conversar(new ComandoMensaje(TURISTA, "hola", "es", null));

        assertThat(consultas).isEmpty();
    }

    @Test
    void siNoHayResultadosConLaPalabraBuscaSinTexto() {
        resultados = List.of();

        servicio.conversar(new ComandoMensaje(TURISTA, "restaurante vegano", "es", null));

        assertThat(consultas).containsExactly("vegano|Gastronomia", "null|Gastronomia");
    }

    /** Cada palabra significativa se busca: el tour de café aparece aunque «Tayrona» vaya primero. */
    @Test
    void buscaConCadaPalabraYUneLosResultadosSinRepetir() {
        servicio.conversar(new ComandoMensaje(TURISTA, "Quiero ir al Tayrona y tomar un tour de café", "es", null));

        assertThat(consultas).containsExactly("tayrona|Tour", "tomar|Tour", "cafe|Tour");
        assertThat(contextos.get(contextos.size() - 1).sugerencias()).hasSize(2);
    }

    @Test
    void unMensajeVacioOLargoEsDatosInvalidos() {
        assertThatThrownBy(() -> servicio.conversar(new ComandoMensaje(TURISTA, "  ", "es", null)))
                .isInstanceOf(DatosInvalidosException.class);
        assertThatThrownBy(() -> servicio.conversar(new ComandoMensaje(TURISTA, "x".repeat(501), "es", null)))
                .isInstanceOf(DatosInvalidosException.class);
        assertThat(uso).isEmpty();
    }
}
