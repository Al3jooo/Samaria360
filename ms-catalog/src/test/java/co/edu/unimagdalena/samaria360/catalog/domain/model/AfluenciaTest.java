package co.edu.unimagdalena.samaria360.catalog.domain.model;

import co.edu.unimagdalena.samaria360.catalog.domain.exception.ContenidoInvalidoException;
import co.edu.unimagdalena.samaria360.catalog.domain.service.PoliticaAlternativos;
import co.edu.unimagdalena.samaria360.catalog.domain.service.PoliticaAlternativos.Alternativo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF39, RF08 y RF51 en el dominio (decisión 2 del equipo; DEC-304, DEC-305). */
class AfluenciaTest {

    /** 10:00 en Santa Marta del 1 de marzo. */
    private static final Instant AHORA = Instant.parse("2030-03-01T15:00:00Z");

    private static Afluencia nivel(int ocupacion, int limite) {
        return Afluencia.calcular(new Afluencia.Lectura(ocupacion, AHORA, limite), AHORA).orElseThrow();
    }

    @Test
    void losUmbralesSonLosDelEquipo() {
        assertThat(nivel(39, 100).nivel()).isEqualTo(Afluencia.Nivel.Bajo);
        assertThat(nivel(40, 100).nivel()).isEqualTo(Afluencia.Nivel.Medio);
        assertThat(nivel(80, 100).nivel()).isEqualTo(Afluencia.Nivel.Medio);
        assertThat(nivel(81, 100).nivel()).isEqualTo(Afluencia.Nivel.Alto);
        assertThat(nivel(99, 100).limiteAlcanzado()).isFalse();
        assertThat(nivel(100, 100).limiteAlcanzado()).isTrue();
        assertThat(nivel(150, 100).porcentaje()).isEqualTo(150);
    }

    @Test
    void soloCuentaLaLecturaDelDiaEnSantaMarta() {
        // 23:30 del 28 de febrero en Santa Marta: otro día, aunque en UTC ya sea 1 de marzo.
        Instant ayer = Instant.parse("2030-03-01T04:30:00Z");
        assertThat(Afluencia.calcular(new Afluencia.Lectura(10, ayer, 100), AHORA)).isEmpty();
        assertThat(Afluencia.calcular(new Afluencia.Lectura(10, AHORA, null), AHORA)).isEmpty();
        assertThat(Afluencia.calcular(null, AHORA)).isEmpty();
    }

    @Test
    void laOcupacionVaDe0AUnMillon() {
        assertThat(Afluencia.ocupacionValida(0)).isZero();
        assertThatThrownBy(() -> Afluencia.ocupacionValida(-1)).isInstanceOf(ContenidoInvalidoException.class);
        assertThatThrownBy(() -> Afluencia.ocupacionValida(1_000_001)).isInstanceOf(ContenidoInvalidoException.class);
        assertThatThrownBy(() -> Afluencia.ocupacionValida(null)).isInstanceOf(ContenidoInvalidoException.class);
    }

    private static AtractivoOcupacion atractivo(String id, String categoria, double lng, Integer ocupacion,
                                                EstadoOperativo estado) {
        return new AtractivoOcupacion(id, "A" + id, categoria, null, new Coordenada(lng, 11.2), estado,
                ocupacion == null ? new Afluencia.Lectura(0, null, 100) : new Afluencia.Lectura(ocupacion, AHORA, 100));
    }

    /** DEC-305: misma categoría, abiertos, de menor ocupación; a igual ocupación, el más cercano; hasta 3. */
    @Test
    void losAlternativosSonSimilaresYDeMenorConcurrencia() {
        AtractivoOcupacion origen = atractivo("o", "Naturaleza", -74.20, 100, EstadoOperativo.Abierto);
        List<Alternativo> alternativos = new PoliticaAlternativos().sugerir(origen, List.of(origen,
                atractivo("lejos", "Naturaleza", -74.50, 20, EstadoOperativo.Abierto),
                atractivo("cerca", "Naturaleza", -74.21, 20, EstadoOperativo.Abierto),
                atractivo("vacio", "Naturaleza", -74.21, 5, EstadoOperativo.Abierto),
                atractivo("lleno", "Naturaleza", -74.21, 100, EstadoOperativo.Abierto),
                atractivo("cerrado", "Naturaleza", -74.21, 1, EstadoOperativo.Cerrado_temporalmente),
                atractivo("cultura", "Cultura", -74.21, 1, EstadoOperativo.Abierto),
                atractivo("sinDatos", "Naturaleza", -74.21, null, EstadoOperativo.Abierto),
                atractivo("otro", "Naturaleza", -74.22, 30, EstadoOperativo.Abierto)), AHORA);

        assertThat(alternativos).extracting(a -> a.atractivo().id()).containsExactly("vacio", "cerca", "lejos");
    }

    @Test
    void sinLecturaDelOrigenSeSugierenLosConDatosYLuegoLosDemas() {
        AtractivoOcupacion origen = atractivo("o", "Cultura", -74.20, null, EstadoOperativo.Abierto);
        List<Alternativo> alternativos = new PoliticaAlternativos().sugerir(origen, List.of(
                atractivo("sinDatos", "Cultura", -74.20, null, EstadoOperativo.Abierto),
                atractivo("alto", "Cultura", -74.30, 90, EstadoOperativo.Abierto)), AHORA);

        assertThat(alternativos).extracting(a -> a.atractivo().id()).containsExactly("alto", "sinDatos");
    }
}
