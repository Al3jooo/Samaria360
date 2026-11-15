package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.auth.application.port.out.TokenProviderPort;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoCuenta;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import co.edu.unimagdalena.samaria360.auth.domain.model.Usuario;
import co.edu.unimagdalena.samaria360.auth.infrastructure.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** API REST de RF15 de punta a punta: MockMvc + Spring Security + PostgreSQL (Testcontainers). */
@PruebaIntegracion
class AuthApiIntegracionTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String ME = "/api/v1/auth/me";
    private static final String RENOVACION = "/api/v1/auth/sesion/renovacion";
    private static final String JWKS = "/api/v1/auth/.well-known/jwks.json";
    private static final String CORREO_TURISTA = "turista@samaria360.test";
    private static final String CONTRASENA_SEMILLA = "Samaria360*";
    private static final UUID ID_TURISTA = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TokenProviderPort tokens;

    @BeforeEach
    void restablecerCuentas() {
        jdbc.update("UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL, estado_cuenta = 'Activo'"
                + " WHERE correo <> 'prestador2@samaria360.test'"); // prestador2 es Inactivo por semilla (V3)
    }

    @Test
    void loginCorrectoEmiteUnTokenValidoSegunLaClaveDelJwks() throws Exception {
        MvcResult resultado = login(CORREO_TURISTA, CONTRASENA_SEMILLA);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        String cuerpo = cuerpo(resultado);
        assertThat(JsonPath.<String>read(cuerpo, "$.tipo")).isEqualTo("Bearer");
        assertThat(JsonPath.<String>read(cuerpo, "$.idUsuario")).isEqualTo(ID_TURISTA.toString());
        assertThat(JsonPath.<String>read(cuerpo, "$.rol")).isEqualTo("Turista");

        SignedJWT jwt = SignedJWT.parse(JsonPath.read(cuerpo, "$.token"));
        JWKSet jwks = JWKSet.parse(cuerpo(mvc.perform(get(JWKS)).andReturn()));
        RSAKey clave = (RSAKey) jwks.getKeyByKeyId(jwt.getHeader().getKeyID());
        assertThat(clave).as("clave del JWKS con el kid del token").isNotNull();
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwt.verify(new RSASSAVerifier(clave))).isTrue();

        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("samaria360-auth");
        assertThat(claims.getSubject()).isEqualTo(ID_TURISTA.toString());
        assertThat(claims.getStringClaim("rol")).isEqualTo("Turista");
        assertThat(claims.getExpirationTime()).isAfter(new Date());
        assertThat(claims.getIssueTime()).isNotNull();
        assertThat(Instant.parse(JsonPath.read(cuerpo, "$.expiraEn")))
                .isEqualTo(claims.getExpirationTime().toInstant());
    }

    @Test
    void correoInexistenteYContrasenaIncorrectaDanRespuestasIdenticas() throws Exception {
        MvcResult sinCuenta = login("nadie@samaria360.test", CONTRASENA_SEMILLA);
        MvcResult claveMala = login(CORREO_TURISTA, "incorrecta");

        assertThat(sinCuenta.getResponse().getStatus()).isEqualTo(401);
        assertThat(claveMala.getResponse().getStatus()).isEqualTo(401);
        assertThat(cuerpo(sinCuenta)).isEqualTo(cuerpo(claveMala));
        assertThat(sinCuenta.getResponse().getContentType()).isEqualTo(claveMala.getResponse().getContentType());
        assertThat(JsonPath.<String>read(cuerpo(sinCuenta), "$.codigo")).isEqualTo("CREDENCIALES_INVALIDAS");
    }

    @Test
    void elTercerFalloResponde423ConCuentaBloqueada() throws Exception {
        login(CORREO_TURISTA, "incorrecta");
        login(CORREO_TURISTA, "incorrecta");
        MvcResult tercero = login(CORREO_TURISTA, "incorrecta");

        assertThat(tercero.getResponse().getStatus()).isEqualTo(423);
        assertThat(tercero.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String cuerpo = cuerpo(tercero);
        assertThat(JsonPath.<String>read(cuerpo, "$.codigo")).isEqualTo("CUENTA_BLOQUEADA");
        assertThat(Instant.parse(JsonPath.read(cuerpo, "$.bloqueadoHasta"))).isAfter(Instant.now());
    }

    @Test
    void unaCuentaInactivaResponde403() throws Exception {
        jdbc.update("UPDATE usuario SET estado_cuenta = 'Inactivo' WHERE correo = 'turista@samaria360.test'");

        MvcResult resultado = login(CORREO_TURISTA, CONTRASENA_SEMILLA);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("CUENTA_INACTIVA");
    }

    /** Ajuste aprobado: sin la contraseña correcta no se revela que la cuenta está inactiva. */
    @Test
    void unaCuentaInactivaConContrasenaIncorrectaResponde401YSumaUnIntento() throws Exception {
        jdbc.update("UPDATE usuario SET estado_cuenta = 'Inactivo' WHERE correo = 'turista@samaria360.test'");

        MvcResult resultado = login(CORREO_TURISTA, "incorrecta");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("CREDENCIALES_INVALIDAS");
        assertThat(jdbc.queryForObject("SELECT intentos_fallidos FROM usuario WHERE id_usuario = ?", Integer.class,
                ID_TURISTA)).isEqualTo(1);
    }

    @Test
    void unPrestadorPendienteResponde403ConSuCodigo() throws Exception {
        jdbc.update("UPDATE prestador SET estado_validacion = 'Pendiente'"
                + " WHERE id_usuario = '00000000-0000-4000-8000-000000000002'");
        try {
            MvcResult resultado = login("prestador@samaria360.test", CONTRASENA_SEMILLA);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
            assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("CUENTA_PENDIENTE_VALIDACION");
            assertThat(cuerpo(resultado)).doesNotContain("token");
        } finally {
            jdbc.update("UPDATE prestador SET estado_validacion = 'Activo'"
                    + " WHERE id_usuario = '00000000-0000-4000-8000-000000000002'");
        }
    }

    @Test
    void elPrestadorInactivoDeLaSemillaResponde403SinPrepararDatos() throws Exception {
        MvcResult resultado = login("prestador2@samaria360.test", CONTRASENA_SEMILLA);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("CUENTA_INACTIVA");
    }

    @Test
    void meSinTokenResponde401() throws Exception {
        MvcResult resultado = mvc.perform(get(ME)).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        assertThat(resultado.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("NO_AUTENTICADO");
    }

    @Test
    void meConTokenDevuelveElNombreDelTurista() throws Exception {
        String cuerpo = cuerpo(me(token(CORREO_TURISTA)));

        assertThat(JsonPath.<String>read(cuerpo, "$.idUsuario")).isEqualTo(ID_TURISTA.toString());
        assertThat(JsonPath.<String>read(cuerpo, "$.correo")).isEqualTo(CORREO_TURISTA);
        assertThat(JsonPath.<String>read(cuerpo, "$.rol")).isEqualTo("Turista");
        assertThat(JsonPath.<String>read(cuerpo, "$.estadoCuenta")).isEqualTo("Activo");
        assertThat(JsonPath.<String>read(cuerpo, "$.nombreVisible")).isEqualTo("Ana Pérez");
    }

    @Test
    void meDelGestorUsaLaParteDelCorreoComoNombre() throws Exception {
        String cuerpo = cuerpo(me(token("gestor@samaria360.test")));

        assertThat(JsonPath.<String>read(cuerpo, "$.rol")).isEqualTo("Gestor");
        assertThat(JsonPath.<String>read(cuerpo, "$.nombreVisible")).isEqualTo("gestor");
    }

    @Test
    void meConTokenVigenteDeUnaCuentaDesactivadaResponde403() throws Exception {
        String token = token(CORREO_TURISTA);
        jdbc.update("UPDATE usuario SET estado_cuenta = 'Desactivado' WHERE correo = 'turista@samaria360.test'");

        MvcResult resultado = me(token);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("CUENTA_INACTIVA");
    }

    @Test
    void meConTokenValidoDeUnUsuarioQueYaNoExisteResponde401() throws Exception {
        Usuario inexistente = new Usuario(UUID.randomUUID(), "borrado@samaria360.test", "$2a$10$hash",
                Rol.Turista, EstadoCuenta.Activo, 0, null);
        String token = tokens.emitir(inexistente, Instant.now()).token();

        MvcResult resultado = me(token);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("NO_AUTENTICADO");
    }

    /** DEC-317: renovar devuelve un token nuevo con el mismo inicio de sesión (ini) y la forma del login. */
    @Test
    void renovarLaSesionEmiteOtroTokenQueConservaElInicio() throws Exception {
        String token = token(CORREO_TURISTA);
        long ini = SignedJWT.parse(token).getJWTClaimsSet().getLongClaim("ini");

        MvcResult resultado = renovar(token);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        String nuevo = JsonPath.read(cuerpo(resultado), "$.token");
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.tipo")).isEqualTo("Bearer");
        assertThat(SignedJWT.parse(nuevo).getJWTClaimsSet().getLongClaim("ini")).isEqualTo(ini);
        assertThat(me(nuevo).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void renovarSinTokenResponde401() throws Exception {
        assertThat(mvc.perform(post(RENOVACION)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void unaSesionDeMasDeOchoHorasNoSeRenueva() throws Exception {
        Usuario turista = new Usuario(ID_TURISTA, CORREO_TURISTA, "$2a$10$hash", Rol.Turista, EstadoCuenta.Activo,
                0, null);
        Instant ahora = Instant.now();
        String token = tokens.emitir(turista, ahora, ahora.minusSeconds(8 * 3600 + 60)).token();

        MvcResult resultado = renovar(token);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("SESION_VENCIDA");
    }

    @Test
    void unaCuentaDesactivadaNoRenuevaLaSesion() throws Exception {
        String token = token(CORREO_TURISTA);
        jdbc.update("UPDATE usuario SET estado_cuenta = 'Desactivado' WHERE correo = 'turista@samaria360.test'");

        MvcResult resultado = renovar(token);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(403);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("CUENTA_INACTIVA");
    }

    @Test
    void elJwksPublicaLaClavePublicaConSuKid() throws Exception {
        MvcResult resultado = mvc.perform(get(JWKS)).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> claves = JsonPath.read(cuerpo(resultado), "$.keys");
        assertThat(claves).hasSize(1);
        Map<String, Object> clave = claves.get(0);
        assertThat(clave).containsEntry("kty", "RSA").containsEntry("alg", "RS256").containsEntry("use", "sig");
        assertThat((String) clave.get("kid")).isNotBlank();
        assertThat(clave).containsKeys("n", "e").doesNotContainKeys("d", "p", "q", "dp", "dq", "qi");
    }

    @Test
    void unCorreoConFormatoInvalidoResponde400() throws Exception {
        MvcResult resultado = login("no-es-un-correo", CONTRASENA_SEMILLA);

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        String cuerpo = cuerpo(resultado);
        assertThat(JsonPath.<String>read(cuerpo, "$.codigo")).isEqualTo("DATOS_INVALIDOS");
        // Códigos, no textos: el frontend los traduce (RF03).
        assertThat(JsonPath.<String>read(cuerpo, "$.campos.correo")).isEqualTo("FORMATO");
    }

    @Test
    void elHealthEsDeAccesoLibre() throws Exception {
        assertThat(mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void swaggerNoQuedaAbiertoFueraDelPerfilDev() throws Exception {
        assertThat(mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    private MvcResult login(String correo, String contrasena) throws Exception {
        String json = "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}";
        return mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private String token(String correo) throws Exception {
        MvcResult resultado = login(correo, CONTRASENA_SEMILLA);
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        return JsonPath.read(cuerpo(resultado), "$.token");
    }

    private MvcResult renovar(String token) throws Exception {
        return mvc.perform(post(RENOVACION).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
    }

    private MvcResult me(String token) throws Exception {
        return mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
    }

    /** UTF-8 explícito: "Ana Pérez" llega con tilde. */
    private static String cuerpo(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
