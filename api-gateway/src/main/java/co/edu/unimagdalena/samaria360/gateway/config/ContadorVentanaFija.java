package co.edu.unimagdalena.samaria360.gateway.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contador en memoria con ventana fija por clave (DEC-271). Una instancia del gateway, sin estado compartido: si
 * algún día hay varias réplicas, cada una cuenta por su cuenta (el límite efectivo se multiplica; ver ADR-006). Las
 * ventanas vencidas se purgan al pasar de {@link #PURGA_DESDE} claves, para que el mapa no crezca sin límite.
 */
public class ContadorVentanaFija {

    static final int PURGA_DESDE = 10_000;

    private record Ventana(Instant inicio, Duration duracion, int usadas) {

        boolean vencida(Instant ahora) {
            return !ahora.isBefore(inicio.plus(duracion));
        }
    }

    private final Clock reloj;
    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    public ContadorVentanaFija(Clock reloj) {
        this.reloj = reloj;
    }

    /**
     * Cuenta una petición de {@code clave}.
     *
     * @return vacío si se admite; si no, cuánto falta para que empiece la próxima ventana (para {@code Retry-After})
     */
    public Optional<Duration> registrar(String clave, LimiteTasaPropiedades.Regla regla) {
        Instant ahora = reloj.instant();
        if (ventanas.size() > PURGA_DESDE) {
            ventanas.values().removeIf(ventana -> ventana.vencida(ahora));
        }
        Ventana actual = ventanas.compute(clave, (k, ventana) -> {
            if (ventana == null || ventana.vencida(ahora)) {
                return new Ventana(ahora, regla.ventana(), 1);
            }
            return new Ventana(ventana.inicio(), ventana.duracion(), ventana.usadas() + 1);
        });
        if (actual.usadas() <= regla.maximo()) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(ahora, actual.inicio().plus(actual.duracion())));
    }

    int claves() {
        return ventanas.size();
    }
}
