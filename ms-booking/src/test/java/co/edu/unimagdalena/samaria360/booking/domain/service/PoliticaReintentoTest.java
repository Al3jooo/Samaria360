package co.edu.unimagdalena.samaria360.booking.domain.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaReintentoTest {

    private final PoliticaReintento politica = new PoliticaReintento();

    @Test
    void laEsperaCreceAlDobleHastaElTopeDe60Segundos() {
        assertThat(politica.espera(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(politica.espera(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(politica.espera(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(politica.espera(4)).isEqualTo(Duration.ofSeconds(16));
        assertThat(politica.espera(5)).isEqualTo(Duration.ofSeconds(32));
        assertThat(politica.espera(6)).isEqualTo(Duration.ofSeconds(60));
        assertThat(politica.espera(500)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void elIntentoDebeSerAlMenosUno() {
        assertThatThrownBy(() -> politica.espera(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
