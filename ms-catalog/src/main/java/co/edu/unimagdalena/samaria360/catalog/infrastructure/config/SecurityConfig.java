package co.edu.unimagdalena.samaria360.catalog.infrastructure.config;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.VerificarSesionUseCase;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.error.ProblemDetailAccessDeniedHandler;
import co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.in.web.error.ProblemDetailAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API sin estado con token Bearer emitido por ms-auth. La búsqueda, sus opciones de filtro, el detalle de un servicio,
 * las preguntas frecuentes, los avisos activos, el detalle de un atractivo y sus imágenes son públicos (WF-04, WF-05,
 * WF-06, WF-13, WF-14); {@code /api/v1/catalogo/admin/**} es solo del Administrador y
 * {@code /api/v1/catalogo/gestor/**}, solo del Gestor (RNF06). El JWT se valida con el JWKS de ms-auth.
 * <p>
 * Dos cadenas (ADR-008): {@code /internal/**} solo para el JWT de servicio de ms-auth ({@code rol = SERVICIO},
 * {@code aud = ms-catalog}); todo lo demás, para tokens de usuario cuyo claim {@code ver} no sea menor que la versión de
 * la proyección {@code cuentas} (DEC-29).
 */
@Configuration
public class SecurityConfig {

    /** Tokens de usuario: firma, iss, exp y, después, {@code ver}. */
    @Bean
    public JwtDecoder jwtDecoder(SeguridadPropiedades propiedades, VerificarSesionUseCase sesiones) {
        return decoder(propiedades, new ValidadorVersionSesion(sesiones));
    }

    /** El JWKS se descarga al validar el primer token: el servicio arranca aunque ms-auth no esté disponible. */
    private static JwtDecoder decoder(SeguridadPropiedades propiedades, OAuth2TokenValidator<Jwt> adicional) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(propiedades.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(propiedades.emisor()), adicional));
        return decoder;
    }

    /** Endpoints internos: solo el JWT de servicio de ms-auth. Un token de usuario recibe 403; una {@code aud} ajena, 401. */
    @Bean
    @Order(1)
    public SecurityFilterChain cadenaInterna(HttpSecurity http, SeguridadPropiedades propiedades,
                                             ProblemDetailAuthenticationEntryPoint entryPoint,
                                             ProblemDetailAccessDeniedHandler accesoDenegado) throws Exception {
        JwtDecoder decoderDeServicio = decoder(propiedades, new ValidadorTokenServicio(propiedades.audiencia()));
        http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(acceso -> acceso.anyRequest().hasRole("SERVICIO"))
                .oauth2ResourceServer(servidor -> servidor
                        .jwt(jwt -> jwt.decoder(decoderDeServicio).jwtAuthenticationConverter(conversorDeRol()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accesoDenegado))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accesoDenegado));
        return http.build();
    }

    @Bean
    @Order(2)
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
                    acceso.requestMatchers(HttpMethod.GET, "/api/v1/catalogo/oferta", "/api/v1/catalogo/filtros",
                            "/api/v1/catalogo/servicios/*", "/api/v1/catalogo/faq",
                            "/api/v1/catalogo/avisos").permitAll();
                    // Detalle de atractivo (DEC-217) e imágenes de GridFS (DEC-209): públicos, como la ficha.
                    acceso.requestMatchers(HttpMethod.GET, "/api/v1/catalogo/atractivos/*",
                            "/api/v1/catalogo/atractivos/*/afluencia", "/api/v1/catalogo/imagenes/*").permitAll();
                    // Agenda de eventos (RF25; DEC-239): pública, como los avisos.
                    acceso.requestMatchers(HttpMethod.GET, "/api/v1/catalogo/eventos").permitAll();
                    // Preguntas frecuentes y avisos (RF26, RF40; DEC-65): con ver vigente, por la misma cadena.
                    acceso.requestMatchers("/api/v1/catalogo/admin/**").hasRole("Administrador");
                    // Favoritos e historial de búsquedas (RF54, RF60; DEC-80, DEC-56): solo el Turista, con ver vigente.
                    acceso.requestMatchers("/api/v1/catalogo/favoritos", "/api/v1/catalogo/favoritos/**",
                            "/api/v1/catalogo/historial").hasRole("Turista");
                    // Registrar servicio y «Mis servicios» (RF09, DEC-129): solo el Prestador, con ver vigente. La
                    // cuenta Activa con RNT (DEC-125) la exige el caso de uso con la proyección cuentas.
                    acceso.requestMatchers("/api/v1/catalogo/prestador/**").hasRole("Prestador");
                    // Gestión de atractivos (RF06, RF07, RF24, RF27, RF33, RF58; DEC-206, DEC-220): solo el Gestor,
                    // con ver vigente. Un Gestor sin fila en cuentas ya recibió 401 SESION_INVALIDADA (DEC-110).
                    acceso.requestMatchers("/api/v1/catalogo/gestor/**").hasRole("Gestor");
                    acceso.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
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

    /** Claim {@code rol: Gestor} → authority {@code ROLE_Gestor}; {@code rol: SERVICIO} → {@code ROLE_SERVICIO} (RNF06). */
    private static JwtAuthenticationConverter conversorDeRol() {
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName("rol");
        autoridades.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(autoridades);
        return conversor;
    }
}
