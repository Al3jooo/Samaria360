package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.auth.application.port.out.PasswordHasherPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.TokenProviderPort;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoCuenta;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import co.edu.unimagdalena.samaria360.auth.domain.model.Usuario;
import co.edu.unimagdalena.samaria360.auth.infrastructure.PruebaIntegracion;
import co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.security.JwtServicioAdapter;
import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * «Administrar cuentas» de punta a punta: MockMvc + Spring Security + PostgreSQL (Testcontainers). Cada prueba crea sus
 * propios usuarios (con una contraseña aleatoria) para no tocar la versión de las cuentas de la semilla.
 */
@PruebaIntegracion
class AdminCuentasApiIntegracionTest {

    private static final String BASE = "/api/v1/auth/admin";
    private static final UUID ID_ADMIN = UUID.fromString("00000000-0000-4000-8000-000000000004");
    private static final UUID ID_TURISTA_SEMILLA = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String CONTRASENA = UUID.randomUUID().toString();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TokenProviderPort tokens;

    @Autowired
    private PasswordHasherPort hasher;

    @Autowired
    private JwtServicioAdapter tokensDeServicio;

    // ---- acceso -----------------------------------------------------------------------------------------------

    @Test
    void unUsuarioQueNoEsAdministradorRecibe403EnTodasLasRutas() throws Exception {
        String turista = tokenDe(new Usuario(ID_TURISTA_SEMILLA, "turista@samaria360.test", "x", Rol.Turista,
                EstadoCuenta.Activo, 0, null));
        UUID cualquiera = UUID.randomUUID();

        for (MockHttpServletRequestBuilder solicitud : List.of(
                get(BASE + "/prestadores"), get(BASE + "/turistas"), get(BASE + "/prestadores/" + cualquiera),
                post(BASE + "/prestadores/" + cualquiera + "/validacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"Aprobar\"}"),
                post(BASE + "/prestadores/" + cualquiera + "/inactivacion"),
                post(BASE + "/turistas/" + cualquiera + "/desactivacion"),
                post(BASE + "/turistas/" + cualquiera + "/reactivacion"))) {
            MvcResult resultado = mvc.perform(solicitud.header(HttpHeaders.AUTHORIZATION, "Bearer " + turista))
                    .andReturn();
            assertThat(resultado.getResponse().getStatus()).as(solicitud.toString()).isEqualTo(403);
            assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("ACCESO_DENEGADO");
        }
    }

    @Test
    void sinTokenResponde401() throws Exception {
        MvcResult resultado = mvc.perform(get(BASE + "/prestadores")).andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("NO_AUTENTICADO");
    }

    @Test
    void unTokenDeServicioNoSirveEnLaApiPublica() throws Exception {
        String servicio = tokensDeServicio.emitir(Destino.MS_CATALOG, Instant.now());

        MvcResult resultado = mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + servicio))
                .andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("NO_AUTENTICADO");
    }

    // ---- listados y detalle -----------------------------------------------------------------------------------

    @Test
    void listaLosPrestadoresPorEstadoYBuscaPorNombreCorreoORnt() throws Exception {
        String marca = marca();
        UUID pendiente = crearPrestador("Pe" + marca, "RNT-P" + marca, "Pendiente", "Activo", null);
        crearPrestador("Ac" + marca, "RNT-A" + marca, "Activo", "Activo", null);
        crearPrestador("Re" + marca, "RNT-R" + marca, "Rechazado", "Activo", "Sin documentos");
        crearPrestador("In" + marca, "RNT-I" + marca, "Activo", "Inactivo", null);

        String pendientes = cuerpo(admin(get(BASE + "/prestadores?estado=Pendiente&q=" + marca)));
        assertThat(JsonPath.<Integer>read(pendientes, "$.totalElementos")).isEqualTo(1);
        assertThat(JsonPath.<String>read(pendientes, "$.elementos[0].idUsuario")).isEqualTo(pendiente.toString());
        assertThat(JsonPath.<String>read(pendientes, "$.elementos[0].representanteLegal")).isEqualTo("Pe" + marca);
        assertThat(JsonPath.<String>read(pendientes, "$.elementos[0].rnt")).isEqualTo("RNT-P" + marca);
        assertThat(JsonPath.<String>read(pendientes, "$.elementos[0].estado")).isEqualTo("Pendiente");
        assertThat(JsonPath.<Integer>read(pendientes, "$.tamano")).isEqualTo(10);
        assertThat(JsonPath.<Integer>read(pendientes, "$.pagina")).isZero();

        String todos = cuerpo(admin(get(BASE + "/prestadores?q=" + marca)));
        assertThat(JsonPath.<Integer>read(todos, "$.totalElementos")).isEqualTo(4);
        assertThat(JsonPath.<List<String>>read(todos, "$.elementos[*].estado"))
                .containsExactlyInAnyOrder("Pendiente", "Activo", "Rechazado", "Inactivo");

        String porRnt = cuerpo(admin(get(BASE + "/prestadores?q=rnt-p" + marca.toLowerCase())));
        assertThat(JsonPath.<Integer>read(porRnt, "$.totalElementos")).isEqualTo(1);
        String porCorreo = cuerpo(admin(get(BASE + "/prestadores?q=" + "pe" + marca.toLowerCase() + "@")));
        assertThat(JsonPath.<Integer>read(porCorreo, "$.totalElementos")).isEqualTo(1);
    }

    @Test
    void elPrestadorInactivoDeLaSemillaSeListaComoInactivo() throws Exception {
        String cuerpo = cuerpo(admin(get(BASE + "/prestadores?estado=Inactivo&q=Laura")));

        assertThat(JsonPath.<Integer>read(cuerpo, "$.totalElementos")).isEqualTo(1);
        assertThat(JsonPath.<String>read(cuerpo, "$.elementos[0].representanteLegal")).isEqualTo("Laura Martínez");
        assertThat(JsonPath.<String>read(cuerpo, "$.elementos[0].estado")).isEqualTo("Inactivo");
    }

    @Test
    void elTextoSeBuscaLiteralYNoComoComodin() throws Exception {
        String cuerpo = cuerpo(admin(get(BASE + "/prestadores?q=%25")));

        assertThat(JsonPath.<Integer>read(cuerpo, "$.totalElementos")).isZero();
    }

    @Test
    void elDetalleTraeLosDatosOrganizacionalesYElMotivoDelRechazo() throws Exception {
        String marca = marca();
        UUID id = crearPrestador("De" + marca, "RNT-D" + marca, "Rechazado", "Activo", "RNT no vigente");

        String cuerpo = cuerpo(admin(get(BASE + "/prestadores/" + id)));

        assertThat(JsonPath.<String>read(cuerpo, "$.idUsuario")).isEqualTo(id.toString());
        assertThat(JsonPath.<String>read(cuerpo, "$.representanteLegal")).isEqualTo("De" + marca);
        assertThat(JsonPath.<String>read(cuerpo, "$.rnt")).isEqualTo("RNT-D" + marca);
        assertThat(JsonPath.<String>read(cuerpo, "$.correoCorporativo")).isEqualTo("de" + marca.toLowerCase() + "@hotel.test");
        assertThat(JsonPath.<String>read(cuerpo, "$.telefono")).isEqualTo("+57 300 000 0000");
        assertThat(JsonPath.<Boolean>read(cuerpo, "$.aceptoPoliticas")).isTrue();
        assertThat(JsonPath.<String>read(cuerpo, "$.estado")).isEqualTo("Rechazado");
        assertThat(JsonPath.<String>read(cuerpo, "$.motivoRechazo")).isEqualTo("RNT no vigente");
        assertThat(JsonPath.<String>read(cuerpo, "$.fechaRegistro")).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    void unIdDeOtroRolOInexistenteEs404ConSuCodigo() throws Exception {
        MvcResult turista = admin(get(BASE + "/prestadores/" + ID_TURISTA_SEMILLA));
        MvcResult inexistente = admin(post(BASE + "/prestadores/" + UUID.randomUUID() + "/inactivacion"));
        MvcResult prestadorComoTurista = admin(post(BASE + "/turistas/" + UUID.randomUUID() + "/desactivacion"));

        for (MvcResult resultado : List.of(turista, inexistente, prestadorComoTurista)) {
            assertThat(resultado.getResponse().getStatus()).isEqualTo(404);
            assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("CUENTA_NO_ENCONTRADA");
        }
    }

    @Test
    void unEstadoQueNoCorrespondeAlRolEs400ConElCampo() throws Exception {
        MvcResult resultado = admin(get(BASE + "/turistas?estado=Pendiente"));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("DATOS_INVALIDOS");
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.campos.estado")).isEqualTo("NO_PERMITIDO");
    }

    @Test
    void listaLosTuristasConSuPaisYMuestraDesactivadoAlInactivoHeredado() throws Exception {
        String marca = marca();
        crearTurista("Ac" + marca, "Activo");
        crearTurista("De" + marca, "Desactivado");
        crearTurista("In" + marca, "Inactivo"); // dato heredado: Inactivo es el estado de RF22, del prestador

        String desactivados = cuerpo(admin(get(BASE + "/turistas?estado=Desactivado&q=" + marca)));
        assertThat(JsonPath.<Integer>read(desactivados, "$.totalElementos")).isEqualTo(2);
        assertThat(JsonPath.<List<String>>read(desactivados, "$.elementos[*].estado"))
                .containsOnly("Desactivado");

        String todos = cuerpo(admin(get(BASE + "/turistas?q=" + marca)));
        assertThat(JsonPath.<Integer>read(todos, "$.totalElementos")).isEqualTo(3);
        assertThat(JsonPath.<String>read(todos, "$.elementos[0].pais")).isEqualTo("CO");
        assertThat(JsonPath.<String>read(todos, "$.elementos[0].nombre")).startsWith("Nombre");
    }

    // ---- validar (RF28) ---------------------------------------------------------------------------------------

    @Test
    void aprobarDejaAlPrestadorActivoSubeLaVersionYRegistraElEventoParaLosDosDestinos() throws Exception {
        String marca = marca();
        UUID id = crearPrestador("Ap" + marca, "RNT-X" + marca, "Pendiente", "Activo", null);

        MvcResult resultado = admin(post(BASE + "/prestadores/" + id + "/validacion")
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"Aprobar\"}"));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.estado")).isEqualTo("Activo");
        assertThat(version(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado_validacion FROM prestador WHERE id_usuario = ?", String.class, id))
                .isEqualTo("Activo");
        assertThat(jdbc.queryForObject("SELECT revisado_por FROM prestador WHERE id_usuario = ?", UUID.class, id))
                .isEqualTo(ID_ADMIN);
        assertThat(jdbc.queryForObject("SELECT revisado_en IS NOT NULL FROM prestador WHERE id_usuario = ?",
                Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForList("SELECT en.destino FROM outbox_entrega en JOIN outbox_evento e"
                + " ON e.id_evento = en.id_evento WHERE e.id_usuario = ?", String.class, id))
                .containsExactlyInAnyOrder("ms-catalog", "ms-booking");
        Map<String, Object> evento = jdbc.queryForMap("SELECT tipo, carga::text AS carga FROM outbox_evento"
                + " WHERE id_usuario = ?", id);
        assertThat(evento.get("tipo")).isEqualTo("CuentaActualizada");
        assertThat(JsonPath.<String>read((String) evento.get("carga"), "$.estadoCuenta")).isEqualTo("Activo");
        assertThat(JsonPath.<String>read((String) evento.get("carga"), "$.rol")).isEqualTo("Prestador");
        assertThat(JsonPath.<Integer>read((String) evento.get("carga"), "$.version")).isEqualTo(1);
    }

    @Test
    void rechazarGuardaLaJustificacionLaRevisionYElPrestadorRechazadoNoInicia() throws Exception {
        String marca = marca();
        UUID id = crearPrestador("Rz" + marca, "RNT-Z" + marca, "Pendiente", "Activo", null);

        MvcResult resultado = admin(post(BASE + "/prestadores/" + id + "/validacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"Rechazar\",\"justificacion\":\"  El RNT no está vigente  \"}"));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.estado")).isEqualTo("Rechazado");
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.motivoRechazo")).isEqualTo("El RNT no está vigente");
        assertThat(jdbc.queryForObject("SELECT motivo_rechazo FROM prestador WHERE id_usuario = ?", String.class, id))
                .isEqualTo("El RNT no está vigente");

        MvcResult sesion = login("rz" + marca.toLowerCase() + "@hotel.test");
        assertThat(sesion.getResponse().getStatus()).isEqualTo(403);
        assertThat(JsonPath.<String>read(cuerpo(sesion), "$.codigo")).isEqualTo("CUENTA_RECHAZADA");
        // DEC-273: con la contraseña correcta, el prestador recibe la justificación.
        assertThat(JsonPath.<String>read(cuerpo(sesion), "$.motivo")).isEqualTo("El RNT no está vigente");
    }

    @Test
    void unPrestadorAprobadoIniciaSesionConElTokenDeLaVersionNueva() throws Exception {
        String marca = marca();
        UUID id = crearPrestador("Ok" + marca, "RNT-K" + marca, "Pendiente", "Activo", null);
        String correo = "ok" + marca.toLowerCase() + "@hotel.test";
        assertThat(login(correo).getResponse().getStatus()).isEqualTo(403);

        admin(post(BASE + "/prestadores/" + id + "/validacion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"Aprobar\"}"));
        MvcResult sesion = login(correo);

        assertThat(sesion.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(cuerpo(sesion), "$.rol")).isEqualTo("Prestador");
        assertThat(SignedJWT.parse(JsonPath.<String>read(cuerpo(sesion), "$.token")).getJWTClaimsSet()
                .getLongClaim("ver")).isEqualTo(1L);
    }

    @Test
    void rechazarSinJustificacionEs400ConElCampoYNoCambiaNada() throws Exception {
        String marca = marca();
        UUID id = crearPrestador("Sj" + marca, "RNT-S" + marca, "Pendiente", "Activo", null);

        for (String cuerpoSolicitud : List.of("{\"decision\":\"Rechazar\"}",
                "{\"decision\":\"Rechazar\",\"justificacion\":\"   \"}")) {
            MvcResult resultado = admin(post(BASE + "/prestadores/" + id + "/validacion")
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpoSolicitud));

            assertThat(resultado.getResponse().getStatus()).isEqualTo(400);
            assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("DATOS_INVALIDOS");
            assertThat(JsonPath.<String>read(cuerpo(resultado), "$.campos.justificacion")).isEqualTo("REQUERIDO");
        }
        assertThat(version(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT estado_validacion FROM prestador WHERE id_usuario = ?", String.class, id))
                .isEqualTo("Pendiente");
    }

    @Test
    void unaJustificacionDeMasDe500CaracteresYUnaDecisionDesconocidaSon400() throws Exception {
        String marca = marca();
        UUID id = crearPrestador("Lg" + marca, "RNT-L" + marca, "Pendiente", "Activo", null);

        MvcResult larga = admin(post(BASE + "/prestadores/" + id + "/validacion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"Rechazar\",\"justificacion\":\"" + "a".repeat(501) + "\"}"));
        MvcResult desconocida = admin(post(BASE + "/prestadores/" + id + "/validacion")
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"Quizás\"}"));

        assertThat(JsonPath.<String>read(cuerpo(larga), "$.campos.justificacion")).isEqualTo("LONGITUD_MAXIMA");
        assertThat(JsonPath.<String>read(cuerpo(desconocida), "$.campos.decision")).isEqualTo("FORMATO");
    }

    @Test
    void dosValidacionesSimultaneasDanUn200YUn409() throws Exception {
        String marca = marca();
        UUID id = crearPrestador("Cc" + marca, "RNT-C" + marca, "Pendiente", "Activo", null);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            Future<MvcResult> primera = hilos.submit(() -> {
                salida.await();
                return admin(post(BASE + "/prestadores/" + id + "/validacion")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"Aprobar\"}"));
            });
            Future<MvcResult> segunda = hilos.submit(() -> {
                salida.await();
                return admin(post(BASE + "/prestadores/" + id + "/validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"Rechazar\",\"justificacion\":\"Documentos incompletos\"}"));
            });
            salida.countDown();
            List<MvcResult> resultados = List.of(primera.get(), segunda.get());

            assertThat(resultados.stream().map(r -> r.getResponse().getStatus()).sorted().toList())
                    .containsExactly(200, 409);
            MvcResult perdedora = resultados.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst()
                    .orElseThrow();
            assertThat(JsonPath.<String>read(cuerpo(perdedora), "$.codigo")).isEqualTo("ESTADO_CAMBIADO");
            assertThat(JsonPath.<String>read(cuerpo(perdedora), "$.estadoActual")).isIn("Activo", "Rechazado");
            // Un solo cambio y un solo evento.
            assertThat(version(id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE id_usuario = ?", Integer.class, id))
                    .isEqualTo(1);
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    void siFallaElEventoNoQuedaElCambio() throws Exception {
        String marca = marca();
        UUID id = crearPrestador("Rb" + marca, "RNT-B" + marca, "Pendiente", "Activo", null);
        jdbc.execute("CREATE OR REPLACE FUNCTION fallar_outbox() RETURNS trigger LANGUAGE plpgsql"
                + " AS $$ BEGIN RAISE EXCEPTION 'outbox caído'; END $$");
        jdbc.execute("CREATE TRIGGER tg_fallar_outbox BEFORE INSERT ON outbox_evento"
                + " FOR EACH ROW EXECUTE FUNCTION fallar_outbox()");
        try {
            MvcResult resultado = admin(post(BASE + "/prestadores/" + id + "/validacion")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"Aprobar\"}"));

            assertThat(resultado.getResponse().getStatus()).isEqualTo(500);
        } finally {
            jdbc.execute("DROP TRIGGER tg_fallar_outbox ON outbox_evento");
            jdbc.execute("DROP FUNCTION fallar_outbox()");
        }
        assertThat(version(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT estado_validacion FROM prestador WHERE id_usuario = ?", String.class, id))
                .isEqualTo("Pendiente");
        assertThat(jdbc.queryForObject("SELECT revisado_por IS NULL FROM prestador WHERE id_usuario = ?",
                Boolean.class, id)).isTrue();
    }

    @Test
    void validarAUnPrestadorYaValidadoEs409ConElEstadoActual() throws Exception {
        String marca = marca();
        UUID id = crearPrestador("Yv" + marca, "RNT-Y" + marca, "Activo", "Activo", null);

        MvcResult resultado = admin(post(BASE + "/prestadores/" + id + "/validacion")
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"Aprobar\"}"));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("ESTADO_CAMBIADO");
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.estadoActual")).isEqualTo("Activo");
    }

    // ---- inactivar (RF22) -------------------------------------------------------------------------------------

    @Test
    void inactivarUnPrestadorActivoLoDejaInactivoYSinPoderIniciarSesion() throws Exception {
        String marca = marca();
        UUID id = crearPrestador("Ia" + marca, "RNT-N" + marca, "Activo", "Activo", null);
        String correo = "ia" + marca.toLowerCase() + "@hotel.test";
        assertThat(login(correo).getResponse().getStatus()).isEqualTo(200);

        MvcResult resultado = admin(post(BASE + "/prestadores/" + id + "/inactivacion"));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.estado")).isEqualTo("Inactivo");
        assertThat(jdbc.queryForObject("SELECT estado_cuenta::text FROM usuario WHERE id_usuario = ?", String.class, id))
                .isEqualTo("Inactivo");
        MvcResult sesion = login(correo);
        assertThat(sesion.getResponse().getStatus()).isEqualTo(403);
        assertThat(JsonPath.<String>read(cuerpo(sesion), "$.codigo")).isEqualTo("CUENTA_INACTIVA");
        // DEC-37: no hay reactivación del prestador.
        assertThat(admin(post(BASE + "/prestadores/" + id + "/inactivacion")).getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void inactivarUnPrestadorPendienteEs409() throws Exception {
        String marca = marca();
        UUID id = crearPrestador("Ip" + marca, "RNT-W" + marca, "Pendiente", "Activo", null);

        MvcResult resultado = admin(post(BASE + "/prestadores/" + id + "/inactivacion"));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.estadoActual")).isEqualTo("Pendiente");
    }

    // ---- turistas (RF62) y la sesión (DEC-29) -----------------------------------------------------------------

    @Test
    void elTokenTraeVerYVenceALos15Minutos() throws Exception {
        String marca = marca();
        UUID id = crearTurista("Tk" + marca, "Activo");

        MvcResult sesion = login(correoDeTurista("Tk" + marca));

        assertThat(sesion.getResponse().getStatus()).isEqualTo(200);
        JWTClaimsSet claims = SignedJWT.parse(JsonPath.<String>read(cuerpo(sesion), "$.token")).getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(id.toString());
        assertThat(claims.getLongClaim("ver")).isZero();
        assertThat(claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()).isEqualTo(15 * 60 * 1000L);
    }

    @Test
    void desactivarUnTuristaInvalidaSuTokenViejoYNoDejaIniciarSesionHastaReactivarlo() throws Exception {
        String marca = marca();
        UUID id = crearTurista("Ds" + marca, "Activo");
        String correo = correoDeTurista("Ds" + marca);
        String tokenViejo = JsonPath.read(cuerpo(login(correo)), "$.token");
        assertThat(me(tokenViejo).getResponse().getStatus()).isEqualTo(200);

        MvcResult desactivado = admin(post(BASE + "/turistas/" + id + "/desactivacion"));
        assertThat(desactivado.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(cuerpo(desactivado), "$.estado")).isEqualTo("Desactivado");
        assertThat(version(id)).isEqualTo(1);

        MvcResult conTokenViejo = me(tokenViejo);
        assertThat(conTokenViejo.getResponse().getStatus()).isEqualTo(401);
        assertThat(JsonPath.<String>read(cuerpo(conTokenViejo), "$.codigo")).isEqualTo("SESION_INVALIDADA");
        MvcResult sesion = login(correo);
        assertThat(sesion.getResponse().getStatus()).isEqualTo(403);
        assertThat(JsonPath.<String>read(cuerpo(sesion), "$.codigo")).isEqualTo("CUENTA_INACTIVA");

        MvcResult reactivado = admin(post(BASE + "/turistas/" + id + "/reactivacion"));
        assertThat(reactivado.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(cuerpo(reactivado), "$.estado")).isEqualTo("Activo");
        MvcResult nueva = login(correo);
        assertThat(nueva.getResponse().getStatus()).isEqualTo(200);
        String tokenNuevo = JsonPath.read(cuerpo(nueva), "$.token");
        assertThat(SignedJWT.parse(tokenNuevo).getJWTClaimsSet().getLongClaim("ver")).isEqualTo(2L);
        assertThat(me(tokenNuevo).getResponse().getStatus()).isEqualTo(200);
        // El token de antes de reactivar sigue sin servir: la versión solo sube.
        assertThat(me(tokenViejo).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void desactivarUnTuristaYaDesactivadoOReactivarUnoActivoEs409() throws Exception {
        String marca = marca();
        UUID desactivado = crearTurista("Dd" + marca, "Desactivado");
        UUID activo = crearTurista("Ra" + marca, "Activo");

        MvcResult doble = admin(post(BASE + "/turistas/" + desactivado + "/desactivacion"));
        MvcResult innecesaria = admin(post(BASE + "/turistas/" + activo + "/reactivacion"));

        assertThat(doble.getResponse().getStatus()).isEqualTo(409);
        assertThat(JsonPath.<String>read(cuerpo(doble), "$.estadoActual")).isEqualTo("Desactivado");
        assertThat(innecesaria.getResponse().getStatus()).isEqualTo(409);
        assertThat(JsonPath.<String>read(cuerpo(innecesaria), "$.estadoActual")).isEqualTo("Activo");
        assertThat(version(desactivado)).isZero();
    }

    @Test
    void unTuristaInactivoHeredadoSePuedeReactivar() throws Exception {
        String marca = marca();
        UUID id = crearTurista("Ih" + marca, "Inactivo");

        MvcResult resultado = admin(post(BASE + "/turistas/" + id + "/reactivacion"));

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT estado_cuenta::text FROM usuario WHERE id_usuario = ?", String.class, id))
                .isEqualTo("Activo");
    }

    @Test
    void elAdministradorConUnTokenViejoDeSuPropiaCuentaTambienPierdeLaSesion() throws Exception {
        // ms-auth compara «ver» contra su propia tabla en cada endpoint, también en /admin/**.
        String marca = marca();
        UUID id = crearUsuario(4, "ad" + marca.toLowerCase() + "@samaria360.test", "Activo");
        jdbc.update("INSERT INTO administrador (id_usuario) VALUES (?)", id);
        String token = tokenDe(new Usuario(id, "ad" + marca.toLowerCase() + "@samaria360.test", "x", Rol.Administrador,
                EstadoCuenta.Activo, 0, null, 0));
        assertThat(mvc.perform(get(BASE + "/turistas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        jdbc.update("UPDATE usuario SET version_cuenta = 1 WHERE id_usuario = ?", id);

        MvcResult resultado = mvc.perform(get(BASE + "/turistas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("SESION_INVALIDADA");
    }

    // ---- ayudas -----------------------------------------------------------------------------------------------

    private static String marca() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private String tokenDe(Usuario usuario) {
        return tokens.emitir(usuario, Instant.now()).token();
    }

    /** El Administrador de la semilla, con su versión actual: V15 la subió a 1 (DEC-108). */
    private MvcResult admin(MockHttpServletRequestBuilder solicitud) throws Exception {
        String token = tokenDe(new Usuario(ID_ADMIN, "admin@samaria360.test", "x", Rol.Administrador,
                EstadoCuenta.Activo, 0, null, version(ID_ADMIN)));
        return mvc.perform(solicitud.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
    }

    private MvcResult me(String token) throws Exception {
        return mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
    }

    private MvcResult login(String correo) throws Exception {
        String json = "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CONTRASENA + "\"}";
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn();
    }

    private long version(UUID id) {
        Long version = jdbc.queryForObject("SELECT version_cuenta FROM usuario WHERE id_usuario = ?", Long.class, id);
        return version == null ? -1 : version;
    }

    private UUID crearUsuario(int idRol, String correo, String estadoCuenta) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id_usuario, id_rol, correo, contrasena_hash, fecha_registro,"
                + " intentos_fallidos, estado_cuenta) VALUES (?, ?, ?, ?, CURRENT_DATE, 0, ?::estado_cuenta)",
                id, idRol, correo, hasher.codificar(CONTRASENA), estadoCuenta);
        return id;
    }

    /** El nombre del prestador es el representante legal; el correo, en minúsculas, es «nombre@hotel.test». */
    private UUID crearPrestador(String representante, String rnt, String validacion, String cuenta, String motivo) {
        UUID id = crearUsuario(2, representante.toLowerCase() + "@hotel.test", cuenta);
        jdbc.update("INSERT INTO prestador (id_usuario, razon_social, rnt, telefono, nombre_representante_legal,"
                + " correo_corporativo, estado_validacion, acepto_politicas, motivo_rechazo)"
                + " VALUES (?, NULL, ?, '+57 300 000 0000', ?, ?, ?, TRUE, ?)",
                id, rnt, representante, representante.toLowerCase() + "@hotel.test", validacion, motivo);
        return id;
    }

    private static String correoDeTurista(String nombre) {
        return nombre.toLowerCase() + "@mail.test";
    }

    private UUID crearTurista(String nombre, String cuenta) {
        UUID id = crearUsuario(1, correoDeTurista(nombre), cuenta);
        jdbc.update("INSERT INTO turista (id_usuario, primer_nombre, primer_apellido, pais_origen, acepto_politicas)"
                + " VALUES (?, ?, 'Apellido', 'CO', TRUE)", id, "Nombre" + nombre);
        return id;
    }

    private static String cuerpo(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
