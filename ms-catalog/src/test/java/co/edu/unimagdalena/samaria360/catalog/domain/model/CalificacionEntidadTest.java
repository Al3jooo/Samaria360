package co.edu.unimagdalena.samaria360.catalog.domain.model;

import co.edu.unimagdalena.samaria360.catalog.domain.service.PoliticaBusqueda;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code PromedioActualizado} en el dominio del catálogo y el orden por calificación (RF61; DEC-302). */
class CalificacionEntidadTest {

    private static final String ID = "6a0000000000000000000001";

    @Test
    void aceptaElContratoDelEvento() {
        CalificacionEntidad c = CalificacionEntidad.deEvento("atractivo", ID, 4.33, 3, 7);

        assertThat(c.tipo()).isEqualTo(TipoOferta.Atractivo);
        assertThat(CalificacionEntidad.deEvento("servicio", ID, null, 0, 1).promedio()).isNull();
    }

    @Test
    void rechazaLoQueNoCumpleElContrato() {
        assertThatThrownBy(() -> CalificacionEntidad.deEvento("lugar", ID, 4.0, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalificacionEntidad.deEvento("servicio", "x", 4.0, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalificacionEntidad.deEvento("servicio", ID, 6.0, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalificacionEntidad.deEvento("servicio", ID, null, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalificacionEntidad.deEvento("servicio", ID, 4.0, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordenarPorCalificacionPoneLoSinCalificarAlFinal() {
        Coordenada punto = new Coordenada(-74.2, 11.2);
        List<ResultadoOferta> resultados = new ArrayList<>(List.of(
                new ResultadoOferta(TipoOferta.Atractivo, "a", "A", null, null, null, null, punto, null, Set.of(), null,
                        null, null),
                new ResultadoOferta(TipoOferta.Atractivo, "b", "B", null, null, null, null, punto, null, Set.of(), null,
                        3.5, 2L),
                new ResultadoOferta(TipoOferta.Atractivo, "c", "C", null, null, null, null, punto, null, Set.of(), null,
                        4.8, 5L)));

        resultados.sort(new PoliticaBusqueda().comparador(OrdenBusqueda.Calificacion));

        assertThat(resultados).extracting(ResultadoOferta::id).containsExactly("c", "b", "a");
    }
}
