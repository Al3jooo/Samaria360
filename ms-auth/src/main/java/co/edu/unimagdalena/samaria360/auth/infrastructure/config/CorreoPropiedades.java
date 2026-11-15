package co.edu.unimagdalena.samaria360.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Correos de ms-auth (RF34, DEC-282): remitente y origen de la SPA para el enlace de recuperación. El servidor SMTP se
 * configura con {@code spring.mail.*}.
 */
@ConfigurationProperties(prefix = "samaria360.correo")
public record CorreoPropiedades(String remitente, String frontendUrl) {

    public CorreoPropiedades {
        if (remitente == null || remitente.isBlank()) {
            remitente = "no-responder@samaria360.local";
        }
        if (frontendUrl == null || frontendUrl.isBlank()) {
            frontendUrl = "http://localhost:8000";
        }
        frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }
}
