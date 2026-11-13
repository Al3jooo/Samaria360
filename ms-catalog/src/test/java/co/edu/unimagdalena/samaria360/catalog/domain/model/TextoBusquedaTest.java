package co.edu.unimagdalena.samaria360.catalog.domain.model;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Búsqueda sin mayúsculas ni tildes (DEC-274): la regla del dominio y el patrón que usa MongoDB dicen lo mismo. */
class TextoBusquedaTest {

    /** El patrón se evalúa como lo haría MongoDB con {@code $options: "i"}. */
    private static boolean patronEncuentra(String buscado, String texto) {
        return Pattern.compile(TextoBusqueda.patronSinTildes(buscado), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(texto).find();
    }

    @Test
    void normalizarQuitaTildesYDieresisYPasaAMinusculas() {
        assertThat(TextoBusqueda.normalizar("Café Ñandú Pingüino ÁRBOL")).isEqualTo("cafe nandu pinguino arbol");
    }

    @Test
    void contieneNoDistingueTildesNiMayusculasEnNingunSentido() {
        assertThat(TextoBusqueda.contiene("Tour de Café en Minca", "cafe")).isTrue();
        assertThat(TextoBusqueda.contiene("Tour de Cafe", "CAFÉ")).isTrue();
        assertThat(TextoBusqueda.contiene("Catedral Basílica", "basilica")).isTrue();
        assertThat(TextoBusqueda.contiene("Bahía Concha", "playa")).isFalse();
        assertThat(TextoBusqueda.contiene(null, "cafe")).isFalse();
    }

    @Test
    void elPatronEncuentraLasMismasVariantesQueElDominio() {
        assertThat(patronEncuentra("cafe", "Tour de Café en Minca")).isTrue();
        assertThat(patronEncuentra("CAFÉ", "tour de cafe")).isTrue();
        assertThat(patronEncuentra("basilica", "Catedral BASÍLICA")).isTrue();
        assertThat(patronEncuentra("pina", "Jugo de piña")).isTrue();
        assertThat(patronEncuentra("cafe", "Bahía Concha")).isFalse();
    }

    @Test
    void elPatronTrataElTextoComoLiteral() {
        assertThat(TextoBusqueda.patronSinTildes(".*")).isEqualTo("\\.\\*");
        assertThat(patronEncuentra("(", "Taller (Rodadero)")).isTrue();
        assertThat(patronEncuentra(".*", "cualquier cosa")).isFalse();
    }
}
