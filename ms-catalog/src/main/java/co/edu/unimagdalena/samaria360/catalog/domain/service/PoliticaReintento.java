package co.edu.unimagdalena.samaria360.catalog.domain.service;

import java.time.Duration;

/** Espera creciente entre intentos de entrega del relevo (ADR-008): 2 s, 4 s, 8 s… con tope de 60 s. */
public class PoliticaReintento {

    static final Duration BASE = Duration.ofSeconds(2);
    static final Duration TOPE = Duration.ofSeconds(60);

    /** @param intento número del intento que acaba de fallar, desde 1 */
    public Duration espera(int intento) {
        if (intento < 1) {
            throw new IllegalArgumentException("intento debe ser al menos 1");
        }
        if (intento > 5) {
            return TOPE;
        }
        Duration espera = BASE.multipliedBy(1L << (intento - 1));
        return espera.compareTo(TOPE) > 0 ? TOPE : espera;
    }
}
