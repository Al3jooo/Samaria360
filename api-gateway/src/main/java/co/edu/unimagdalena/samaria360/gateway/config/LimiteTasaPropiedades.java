package co.edu.unimagdalena.samaria360.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * Límite de tasa del login y del registro por dirección del cliente (DEC-271, mitigación de ADR-006). Valores en
 * {@code application.yml} ({@code samaria360.limite-tasa.*}); son parámetros técnicos de seguridad, ajustables sin
 * cambiar código.
 *
 * @param login    {@code POST /api/v1/auth/login}
 * @param registro {@code POST /api/v1/auth/registro/**} (turista y prestador comparten el cupo)
 */
@ConfigurationProperties(prefix = "samaria360.limite-tasa")
public record LimiteTasaPropiedades(Regla login, Regla registro) {

    public LimiteTasaPropiedades {
        Objects.requireNonNull(login, "samaria360.limite-tasa.login");
        Objects.requireNonNull(registro, "samaria360.limite-tasa.registro");
    }

    /**
     * @param maximo  peticiones admitidas en la ventana
     * @param ventana duración de la ventana fija
     */
    public record Regla(int maximo, Duration ventana) {

        public Regla {
            if (maximo < 1) {
                throw new IllegalArgumentException("El máximo del límite de tasa debe ser al menos 1");
            }
            Objects.requireNonNull(ventana, "ventana");
            if (ventana.isNegative() || ventana.isZero()) {
                throw new IllegalArgumentException("La ventana del límite de tasa debe ser positiva");
            }
        }
    }
}
