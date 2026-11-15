package co.edu.unimagdalena.samaria360.auth.infrastructure.config;

import co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web.error.ProblemDetailAccessDeniedHandler;
import co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web.error.ProblemDetailAuthenticationEntryPoint;
import co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.security.JwtTokenProviderAdapter;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.core.annotation.Order;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/** API sin estado con token Bearer: ms-auth emite el JWT y también lo valida con su propia clave pública. */
@Configuration
public class SecurityConfig {

    /**
     * Endpoints internos (DEC-280): pedir el token con credenciales de cliente es anónimo; los contactos exigen un JWT de
     * servicio con {@code aud = ms-auth}. Va antes que la cadena pública.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain cadenaInterna(HttpSecurity http, RSAKey llaveJwt, JwtPropiedades propiedades,
                                             ProblemDetailAuthenticationEntryPoint entryPoint,
                                             ProblemDetailAccessDeniedHandler accesoDenegado) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(llaveJwt.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(propiedades.emisor()), new ValidadorTokenCliente()));
        http
                .securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(acceso -> {
                    acceso.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    acceso.requestMatchers(HttpMethod.POST, "/internal/v1/tokens").permitAll();
                    acceso.anyRequest().hasRole("SERVICIO");
                })
                .oauth2ResourceServer(servidor -> servidor
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(conversorDeRol()))
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
                    // Un error interno no debe convertirse en 401 al despachar /error.
                    acceso.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    acceso.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll();
                    // Registro (RF01, RF02) y su lista de países: anónimos.
                    acceso.requestMatchers(HttpMethod.POST, "/api/v1/auth/registro/turista",
                            "/api/v1/auth/registro/prestador").permitAll();
                    acceso.requestMatchers(HttpMethod.GET, "/api/v1/auth/paises").permitAll();
                    // RF34: pedir el enlace y restablecer (respuesta uniforme, ADR-006; límite de tasa en el gateway).
                    acceso.requestMatchers(HttpMethod.POST, "/api/v1/auth/recuperacion",
                            "/api/v1/auth/recuperacion/restablecimiento").permitAll();
                    acceso.requestMatchers(HttpMethod.GET, "/api/v1/auth/.well-known/jwks.json").permitAll();
                    acceso.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    // Administrar cuentas (RF28, RF22, RF62): solo el Administrador (RNF06).
                    acceso.requestMatchers("/api/v1/auth/admin/**").hasRole("Administrador");
                    // Perfil del turista (RF53, DEC-79): solo el Turista; /me sigue abierto a cualquier rol autenticado.
                    acceso.requestMatchers("/api/v1/auth/perfil").hasRole("Turista");
                    // Perfil comercial del Prestador (RF43, DEC-188): solo el Prestador.
                    acceso.requestMatchers("/api/v1/auth/prestador/**").hasRole("Prestador");
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
        autoridades.setAuthoritiesClaimName(JwtTokenProviderAdapter.CLAIM_ROL);
        autoridades.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(autoridades);
        return conversor;
    }
}
