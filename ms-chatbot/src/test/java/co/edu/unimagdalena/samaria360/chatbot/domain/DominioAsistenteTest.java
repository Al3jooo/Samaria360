package co.edu.unimagdalena.samaria360.chatbot.domain;

import co.edu.unimagdalena.samaria360.chatbot.domain.exception.DatosInvalidosException;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.EntradaConocimiento;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Idioma;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Interpretacion;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.ItemCatalogo;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Itinerario;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Sugerencia;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Sugerencia.Criterio;
import co.edu.unimagdalena.samaria360.chatbot.domain.service.InterpreteMensaje;
import co.edu.unimagdalena.samaria360.chatbot.domain.service.PlanificadorItinerario;
import co.edu.unimagdalena.samaria360.chatbot.domain.service.PoliticaJustificacion;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dominio del asistente (RF04, RF05, RF35, RF36, RF59), Java puro. */
class DominioAsistenteTest {

    private static final Instant T0 = Instant.parse("2030-03-01T15:00:00Z");

    static ItemCatalogo atractivo(String id, String categoria, String estado) {
        return new ItemCatalogo("Atractivo", id, "Atractivo " + id, categoria, "", estado, null);
    }

    static ItemCatalogo servicio(String id, String categoria, Double calificacion) {
        return new ItemCatalogo("Servicio", id, "Servicio " + id, categoria, "", null, calificacion);
    }

    @Test
    void entiendeCategoriaItinerarioYDiasEnLosDosIdiomas() {
        InterpreteMensaje interprete = new InterpreteMensaje();

        Interpretacion es = interprete.interpretar("Quiero un itinerario de 3 días por playas y museos");
        Interpretacion en = interprete.interpretar("Recommend a hotel for the weekend");

        assertThat(es.pideItinerario()).isTrue();
        assertThat(es.dias()).isEqualTo(3);
        assertThat(es.categoriaAtractivo()).isEqualTo("Naturaleza");
        assertThat(en.categoriaServicio()).isEqualTo("Alojamiento");
        assertThat(en.dias()).isEqualTo(2);
        assertThat(interprete.interpretar("plan de 30 días").dias()).isEqualTo(Interpretacion.DIAS_MAXIMOS);
        assertThat(interprete.interpretar("Hola").buscaAlgo()).isFalse();
    }

    /** RF36: preferencias antes que popularidad y popularidad antes que disponibilidad; un atractivo cerrado no va. */
    @Test
    void elCriterioSeCalculaConDatosYEnElIdiomaDeLaInterfaz() {
        PoliticaJustificacion politica = new PoliticaJustificacion();
        String a = "6a0000000000000000000001";
        List<Sugerencia> sugerencias = politica.sugerir(List.of(
                servicio("6a0000000000000000000002", "Gastronomia", 4.6),
                servicio("6a0000000000000000000003", "Alojamiento", null),
                atractivo(a, "Naturaleza", "Abierto"),
                atractivo("6a0000000000000000000004", "Naturaleza", "Cerrado temporalmente"),
                servicio("6a0000000000000000000005", "Tour", 3.0)), Set.of("Naturaleza", "Aventura"), Idioma.en);

        assertThat(sugerencias).extracting(Sugerencia::criterio).containsExactly(Criterio.PREFERENCIAS,
                Criterio.PREFERENCIAS, Criterio.POPULARIDAD, Criterio.DISPONIBILIDAD);
        assertThat(sugerencias.subList(0, 2)).extracting(s -> s.item().id()).contains(a);
        assertThat(sugerencias.get(2).justificacion()).isEqualTo("Popular with travelers: average rating 4.6.");
        assertThat(sugerencias).noneMatch(s -> s.item().id().endsWith("4"));
    }

    @Test
    void elItinerarioRepartePorDiasSinInventarActividades() {
        List<Sugerencia> cuatro = new PoliticaJustificacion().sugerir(List.of(
                servicio("6a0000000000000000000011", "Tour", null), servicio("6a0000000000000000000012", "Tour", null),
                servicio("6a0000000000000000000013", "Tour", null), servicio("6a0000000000000000000014", "Tour", null)),
                Set.of(), Idioma.es);

        List<Itinerario.Item> items = new PlanificadorItinerario().planificar(cuatro, 2);

        assertThat(items).hasSize(4);
        assertThat(items).extracting(Itinerario.Item::diaSugerido).containsExactly(1, 1, 2, 2);
        assertThat(new PlanificadorItinerario().planificar(cuatro.subList(0, 1), 3)).hasSize(1);
    }

    @Test
    void unItinerarioSeValidaYSeRenumera() {
        UUID turista = UUID.randomUUID();
        Itinerario itinerario = Itinerario.validar(null, turista, "  Fin de semana ", List.of(
                new Itinerario.Item(9, "servicio", "6a0000000000000000000021", "Tour", 2),
                new Itinerario.Item(3, "atractivo", "6a0000000000000000000022", "Playa", 1)), T0, T0);

        assertThat(itinerario.nombre()).isEqualTo("Fin de semana");
        assertThat(itinerario.items()).extracting(Itinerario.Item::orden).containsExactly(1, 2);
        assertThatThrownBy(() -> Itinerario.validar(null, turista, "", List.of(
                new Itinerario.Item(1, "lugar", "x", "", 9),
                new Itinerario.Item(2, "servicio", "6a0000000000000000000021", "A", 1),
                new Itinerario.Item(3, "servicio", "6a0000000000000000000021", "A", 1)), T0, T0))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos())
                        .containsEntry("nombre", "REQUERIDO").containsEntry("items[0].tipo", "NO_PERMITIDO")
                        .containsEntry("items[0].refId", "FORMATO").containsEntry("items[0].diaSugerido",
                                "VALOR_FUERA_DE_RANGO").containsEntry("items[2].refId", "DUPLICADO"));
    }

    @Test
    void laBaseDeConocimientosAcotaContenidoYCategoria() {
        assertThat(EntradaConocimiento.validar(null, " La playa de Bahía Concha cierra los lunes. ", "Normas", T0)
                .contenido()).isEqualTo("La playa de Bahía Concha cierra los lunes.");
        assertThatThrownBy(() -> EntradaConocimiento.validar(null, "x".repeat(2001), " ", T0))
                .isInstanceOfSatisfying(DatosInvalidosException.class, e -> assertThat(e.getCampos())
                        .containsEntry("contenido", "LONGITUD_MAXIMA").containsEntry("categoria", "REQUERIDO"));
    }
}
