package co.edu.unimagdalena.samaria360.chatbot.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Validación de los JWT emitidos por ms-auth (samaria360.seguridad.*).
 *
 * @param jwkSetUri JWKS de ms-auth (o del API Gateway más adelante)
 * @param emisor    claim {@code iss} esperado
 * @param audiencia {@code aud} que deben llevar los tokens de servicio de los endpoints internos (ADR-008)
 */
@ConfigurationProperties("samaria360.seguridad")
public record SeguridadPropiedades(String jwkSetUri, String emisor, String audiencia) {
}
