package co.edu.unimagdalena.samaria360.booking.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba espejo (H15 de la revisión del 2026-11-12): {@link ValidadorVersionSesion} y {@link ValidadorTokenServicio}
 * están duplicados en ms-catalog y ms-booking, porque no hay librería compartida entre servicios (ADR-001). El mismo
 * archivo, con los mismos casos, está en ms-catalog: si cambia la regla en un servicio, esta prueba debe cambiar en los dos.
 * Solo cambian el paquete y la audiencia propia ({@value #AUDIENCIA}).
 */
class ValidadoresSeguridadEspejoTest {

    private static final String AUDIENCIA = "ms-booking";
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    /** Doble de VerificarSesionUseCase: guarda cada consulta y responde lo configurado. */
    private final List<Object[]> consultas = new ArrayList<>();
    private boolean vigente = true;
    private final ValidadorVersionSesion version = new ValidadorVersionSesion((id, ver, rol) -> {
        consultas.add(new Object[]{id, ver, rol});
        return vigente;
    });
    private final ValidadorTokenServicio servicio = new ValidadorTokenServicio(AUDIENCIA);

    private static Jwt.Builder token(String sub, String rol) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").subject(sub);
        return rol == null ? b : b.claim("rol", rol);
    }

    private static String codigo(OAuth2TokenValidatorResult resultado) {
        return resultado.getErrors().stream().map(OAuth2Error::getErrorCode).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ ValidadorVersionSesion

    @Test
    void unTokenDeServicioNoSirveParaLaApiPublicaYNoConsultaLaProyeccion() {
        OAuth2TokenValidatorResult r = version.validate(token("ms-auth", "SERVICIO").audience(List.of(AUDIENCIA)).build());

        assertThat(codigo(r)).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN);
        assertThat(consultas).isEmpty();
    }

    @Test
    void unSujetoQueNoEsUuidSeRechaza() {
        OAuth2TokenValidatorResult r = version.validate(token("no-es-uuid", "Turista").claim("ver", 1).build());

        assertThat(codigo(r)).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN);
        assertThat(consultas).isEmpty();
    }

    @Test
    void unTokenSinVerCuentaComoCeroYPasaElRol() {
        OAuth2TokenValidatorResult r = version.validate(token(ID.toString(), "Gestor").build());

        assertThat(r.hasErrors()).isFalse();
        assertThat(consultas).singleElement().satisfies(c -> {
            assertThat(c[0]).isEqualTo(ID);
            assertThat(c[1]).isEqualTo(0L);
            assertThat(c[2]).isEqualTo("Gestor");
        });
    }

    @Test
    void unaSesionQueYaNoEstaVigenteEsSesionInvalidada() {
        vigente = false;

        OAuth2TokenValidatorResult r = version.validate(token(ID.toString(), "Turista").claim("ver", 3).build());

        assertThat(codigo(r)).isEqualTo(ValidadorVersionSesion.SESION_INVALIDADA);
        assertThat(consultas).singleElement().satisfies(c -> assertThat(c[1]).isEqualTo(3L));
    }

    // ------------------------------------------------------------------ ValidadorTokenServicio

    @Test
    void unTokenDeServicioConLaAudienciaPropiaEsValido() {
        assertThat(servicio.validate(token("ms-auth", "SERVICIO").audience(List.of(AUDIENCIA)).build()).hasErrors())
                .isFalse();
    }

    @Test
    void unTokenDeServicioConOtraAudienciaOSinAudienciaSeRechaza() {
        assertThat(codigo(servicio.validate(token("ms-auth", "SERVICIO").audience(List.of("otro-servicio")).build())))
                .isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN);
        assertThat(codigo(servicio.validate(token("ms-auth", "SERVICIO").build())))
                .isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN);
    }

    @Test
    void unTokenDeUsuarioPasaElValidadorYLoRechazaLaAutorizacion() {
        // El 403 lo da hasRole("SERVICIO") de la cadena /internal/**, no este validador.
        assertThat(servicio.validate(token(ID.toString(), "Administrador").build()).hasErrors()).isFalse();
    }
}
