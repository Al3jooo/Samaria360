package co.edu.unimagdalena.samaria360.auth.infrastructure.config;

import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Relevo del outbox (samaria360.outbox.*, ADR-008).
 *
 * @param habilitado si el programador corre; las pruebas lo apagan y llaman al caso de uso
 * @param cada       espera entre pasadas de cada destino
 * @param destinos   URI base de los endpoints internos; van en variables de Compose, no son secretos
 */
@ConfigurationProperties("samaria360.outbox")
public record RelevoPropiedades(boolean habilitado, Duration cada, Destinos destinos) {

    public record Destinos(String msCatalog, String msBooking) {

        public String de(Destino destino) {
            return switch (destino) {
                case MS_CATALOG -> msCatalog;
                case MS_BOOKING -> msBooking;
                case RABBITMQ -> throw new IllegalArgumentException("RabbitMQ no es un destino HTTP (ADR-011)");
            };
        }
    }
}
