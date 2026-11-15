package co.edu.unimagdalena.samaria360.chatbot.infrastructure.config;

import co.edu.unimagdalena.samaria360.chatbot.application.port.in.VerificarSesionUseCase;
import co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.in.web.error.ProblemDetailAccessDeniedHandler;
import co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.in.web.error.ProblemDetailAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Zero-Trust (ADR-005): ms-chatbot valida el JWT de ms-auth con el JWKS (firma, {@code iss}, {@code exp}) y el claim
 * {@code ver} contra su proyección de cuentas (DEC-110). Toda la API exige un token; cada ruta, su rol (RNF06).
 */
@Configuration
public class SecurityConfig {

    /** El JWKS se descarga al validar el primer token: el servicio arranca aunque ms-auth no esté disponible. */
    @Bean
    public JwtDecoder jwtDecoder(SeguridadPropiedades propiedades, VerificarSesionUseCase sesiones) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(propiedades.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(propiedades.emisor()), new ValidadorVersionSesion(sesiones)));
        return decoder;
    }

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, Environment entorno, JwtDecoder jwtDecoder,
                                                 ProblemDetailAuthenticationEntryPoint entryPoint,
                                                 ProblemDetailAccessDeniedHandler accesoDenegado) throws Exception {
        boolean dev = entorno.acceptsProfiles(Profiles.of("dev"));
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(acceso -> {
                    acceso.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    acceso.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    // Base de conocimientos (RF05): solo el Administrador.
                    acceso.requestMatchers("/api/v1/chatbot/admin/**").hasRole("Administrador");
                    // Conversación (RF04) e itinerarios (RF35): solo el Turista.
                    acceso.requestMatchers("/api/v1/chatbot/mensajes", "/api/v1/chatbot/itinerarios", "/api/v1/chatbot/itinerarios/**")
                            .hasRole("Turista");
                    if (dev) {
                        acceso.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }
                    acceso.anyRequest().authenticated();
                })
                .oauth2ResourceServer(servidor -> servidor
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(conversorDeRol()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accesoDenegado))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accesoDenegado));
        return http.build();
    }

    /** Claim {@code rol: Turista} → authority {@code ROLE_Turista} (RNF06). */
    private static JwtAuthenticationConverter conversorDeRol() {
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName("rol");
        autoridades.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(autoridades);
        return conversor;
    }
}
