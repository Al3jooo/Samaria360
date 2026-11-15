package co.edu.unimagdalena.samaria360.booking.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** ms-auth interno (DEC-280, DEC-312): URL de la red de Docker y el secreto de cliente de ms-booking (de .env). */
@ConfigurationProperties(prefix = "samaria360.auth")
public record AuthPropiedades(String url, String secreto) {

    @Override
    public String toString() {
        return "AuthPropiedades[" + url + ", ****]";
    }
}
