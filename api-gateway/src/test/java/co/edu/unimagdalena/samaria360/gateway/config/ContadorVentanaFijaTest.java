package co.edu.unimagdalena.samaria360.gateway.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Ventana fija del límite de tasa (DEC-271), con un reloj que avanza a mano. */
class ContadorVentanaFijaTest {

    private static final LimiteTasaPropiedades.Regla TRES_POR_MINUTO =
            new LimiteTasaPropiedades.Regla(3, Duration.ofMinutes(1));

    /** Reloj mutable para mover el tiempo sin esperar. */
    private static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-11-13T15:00:00Z");

        void avanzar(Duration duracion) {
            ahora = ahora.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private final RelojManual reloj = new RelojManual();
    private final ContadorVentanaFija contador = new ContadorVentanaFija(reloj);

    @Test
    void admiteHastaElMaximoYDespuesDiceCuantoFaltaParaLaProximaVentana() {
        for (int i = 0; i < 3; i++) {
            assertThat(contador.registrar("login|1.2.3.4", TRES_POR_MINUTO)).isEmpty();
        }
        reloj.avanzar(Duration.ofSeconds(20));

        assertThat(contador.registrar("login|1.2.3.4", TRES_POR_MINUTO)).contains(Duration.ofSeconds(40));
    }

    @Test
    void alTerminarLaVentanaElConteoEmpiezaDeNuevo() {
        for (int i = 0; i < 4; i++) {
            contador.registrar("login|1.2.3.4", TRES_POR_MINUTO);
        }
        reloj.avanzar(Duration.ofMinutes(1));

        assertThat(contador.registrar("login|1.2.3.4", TRES_POR_MINUTO)).isEmpty();
    }

    @Test
    void cadaClaveTieneSuPropioConteo() {
        for (int i = 0; i < 3; i++) {
            contador.registrar("login|1.2.3.4", TRES_POR_MINUTO);
        }

        assertThat(contador.registrar("login|1.2.3.4", TRES_POR_MINUTO)).isPresent();
        assertThat(contador.registrar("login|5.6.7.8", TRES_POR_MINUTO)).isEmpty();
        assertThat(contador.registrar("registro|1.2.3.4", TRES_POR_MINUTO)).isEmpty();
    }

    @Test
    void lasVentanasVencidasSePurganCuandoHayMuchasClaves() {
        for (int i = 0; i <= ContadorVentanaFija.PURGA_DESDE; i++) {
            contador.registrar("login|ip-" + i, TRES_POR_MINUTO);
        }
        reloj.avanzar(Duration.ofMinutes(2));

        contador.registrar("login|nueva", TRES_POR_MINUTO);

        assertThat(contador.claves()).isEqualTo(1);
    }
}
