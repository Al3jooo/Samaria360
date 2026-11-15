package co.edu.unimagdalena.samaria360.catalog.domain.model;

import co.edu.unimagdalena.samaria360.catalog.domain.exception.ContenidoInvalidoException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Nivel de afluencia de un atractivo (RF39; decisión 2 del equipo, DEC-304; **decisión del equipo, no del SRS** en
 * los umbrales): la ocupación que registra el Gestor sobre el límite de capacidad de carga (RF07). **Bajo** por debajo
 * del 40 %, **Medio** del 40 % al 80 % y **Alto** por encima del 80 %; {@code limiteAlcanzado} al llegar al 100 %
 * (RF08). Solo cuenta la lectura del día en curso en Santa Marta (el «periodo vigente» de RF39).
 */
public record Afluencia(Nivel nivel, int porcentaje, int ocupacion, int limite, Instant fecha, boolean limiteAlcanzado) {

    public enum Nivel { Bajo, Medio, Alto }

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");
    public static final int OCUPACION_MAXIMA = 1_000_000;
    static final int UMBRAL_MEDIO = 40;
    static final int UMBRAL_ALTO = 80;

    public Afluencia {
        Objects.requireNonNull(nivel, "nivel");
        Objects.requireNonNull(fecha, "fecha");
    }

    /** Una lectura de ocupación con el límite del atractivo, tal como está guardada. */
    public record Lectura(int ocupacion, Instant fecha, Integer limite) {
    }

    /** Sin lectura, sin límite o con una lectura de otro día: sin afluencia vigente. */
    public static Optional<Afluencia> calcular(Lectura lectura, Instant ahora) {
        if (lectura == null || lectura.fecha() == null || lectura.limite() == null || lectura.limite() < 1) {
            return Optional.empty();
        }
        if (!LocalDate.ofInstant(lectura.fecha(), ZONA).equals(LocalDate.ofInstant(ahora, ZONA))) {
            return Optional.empty();
        }
        int porcentaje = (int) Math.round(lectura.ocupacion() * 100.0 / lectura.limite());
        Nivel nivel = porcentaje < UMBRAL_MEDIO ? Nivel.Bajo : porcentaje <= UMBRAL_ALTO ? Nivel.Medio : Nivel.Alto;
        return Optional.of(new Afluencia(nivel, porcentaje, lectura.ocupacion(), lectura.limite(), lectura.fecha(),
                lectura.ocupacion() >= lectura.limite()));
    }

    /** La ocupación que registra el Gestor: un entero de 0 a 1 000 000 personas. */
    public static int ocupacionValida(Integer ocupacion) {
        if (ocupacion == null) {
            throw new ContenidoInvalidoException(Map.of("ocupacion", MotivoInvalido.REQUERIDO));
        }
        if (ocupacion < 0) {
            throw new ContenidoInvalidoException(Map.of("ocupacion", MotivoInvalido.VALOR_MINIMO));
        }
        if (ocupacion > OCUPACION_MAXIMA) {
            throw new ContenidoInvalidoException(Map.of("ocupacion", MotivoInvalido.VALOR_MAXIMO));
        }
        return ocupacion;
    }
}
