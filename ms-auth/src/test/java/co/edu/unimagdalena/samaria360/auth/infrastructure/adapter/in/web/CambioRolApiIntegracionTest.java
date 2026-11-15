package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.in.web;

import co.edu.unimagdalena.samaria360.auth.application.port.in.CambiarRolUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoCambioRol;
import co.edu.unimagdalena.samaria360.auth.application.port.out.CambioRolRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.PasswordHasherPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.TokenProviderPort;
import co.edu.unimagdalena.samaria360.auth.domain.exception.UltimoAdministradorException;
import co.edu.unimagdalena.samaria360.auth.domain.model.CambioRol;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoCuenta;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import co.edu.unimagdalena.samaria360.auth.domain.model.Usuario;
import co.edu.unimagdalena.samaria360.auth.infrastructure.PruebaIntegracion;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * «Cambiar rol de cuenta» (RF45) de punta a punta: MockMvc + Spring Security + PostgreSQL (Testcontainers). Cada prueba
 * crea sus propias cuentas; las que tocan a los Administradores de la semilla los restauran en {@code finally}.
 */
@PruebaIntegracion
class CambioRolApiIntegracionTest {

    private static final String BASE = "/api/v1/auth/admin";
    private static final UUID ID_ADMIN = UUID.fromString("00000000-0000-4000-8000-000000000004");
    private static final UUID ID_PRESTADOR_SEMILLA = UUID.fromString("00000000-0000-4000-8000-000000000002");
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
    private CambiarRolUseCase cambiarRol;

    @Autowired
    private CambioRolRepositoryPort repositorio;

    @Autowired
    private TransactionTemplate transaccion;

    // ---- asignar, modificar y revocar -------------------------------------------------------------------------

    @Test
    void asignarGestorAUnTuristaCambiaElRolCreaElSubtipoConservaElTuristaYPublicaElEvento() throws Exception {
        String marca = marca();
        UUID id = crearTurista("Ag" + marca, "Activo");

        MvcResult resultado = cambio(id, "Gestor", "Turista");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        String cuerpo = cuerpo(resultado);
        assertThat(JsonPath.<String>read(cuerpo, "$.idUsuario")).isEqualTo(id.toString());
        assertThat(JsonPath.<String>read(cuerpo, "$.rol")).isEqualTo("Gestor");
        assertThat(JsonPath.<String>read(cuerpo, "$.nombre")).isEqualTo("NombreAg" + marca + " Apellido");
        assertThat(JsonPath.<Boolean>read(cuerpo, "$.revocable")).isTrue();
        assertThat(rol(id)).isEqualTo("Gestor");
        assertThat(existe("gestor", id)).isTrue();
        assertThat(existe("administrador", id)).isFalse();
        assertThat(existe("turista", id)).isTrue();
        assertThat(version(id)).isEqualTo(1);
        assertThat(evento(id, 1)).containsEntry("rol", "Gestor").containsEntry("estadoCuenta", "Activo");
        assertThat(destinos(id)).containsExactlyInAnyOrder("ms-catalog", "ms-booking");
    }

    @Test
    void asignarAdministradorYDespuesModificarAGestorYVolverMuevenLaFilaDelSubtipo() throws Exception {
        UUID id = crearTurista("Ad" + marca(), "Activo");

        assertThat(cambio(id, "Administrador", "Turista").getResponse().getStatus()).isEqualTo(200);
        assertThat(rol(id)).isEqualTo("Administrador");
        assertThat(existe("administrador", id)).isTrue();

        assertThat(cambio(id, "Gestor", "Administrador").getResponse().getStatus()).isEqualTo(200);
        assertThat(existe("administrador", id)).isFalse();
        assertThat(existe("gestor", id)).isTrue();

        assertThat(cambio(id, "Administrador", "Gestor").getResponse().getStatus()).isEqualTo(200);
        assertThat(existe("gestor", id)).isFalse();
        assertThat(existe("administrador", id)).isTrue();
        assertThat(version(id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE id_usuario = ?"
                + " AND tipo = 'CuentaActualizada'", Integer.class, id)).isEqualTo(3);
        // Un RolCambiado por cada cambio, para la auditoría de RF13 (ADR-011).
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE id_usuario = ?"
                + " AND tipo = 'RolCambiado'", Integer.class, id)).isEqualTo(3);
        assertThat(evento(id, 3)).containsEntry("rol", "Administrador");
    }

    @Test
    void revocarBorraElSubtipoConservaElTuristaYPublicaElRolTuristaConLaVersionNueva() throws Exception {
        UUID id = crearTurista("Rv" + marca(), "Activo");
        cambio(id, "Gestor", "Turista");

        MvcResult resultado = cambio(id, "Turista", "Gestor");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.rol")).isEqualTo("Turista");
        assertThat(rol(id)).isEqualTo("Turista");
        assertThat(existe("gestor", id)).isFalse();
        assertThat(existe("turista", id)).isTrue();
        assertThat(version(id)).isEqualTo(2);
        Map<String, Object> carga = evento(id, 2);
        assertThat(carga).containsEntry("idUsuario", id.toString()).containsEntry("rol", "Turista")
                .containsEntry("estadoCuenta", "Activo");
        assertThat(jdbc.queryForList("SELECT en.destino FROM outbox_entrega en JOIN outbox_evento e"
                        + " ON e.id_evento = en.id_evento WHERE e.id_usuario = ? AND (e.carga->>'version')::int = 2"
                        + " AND e.tipo = 'CuentaActualizada'",
                String.class, id)).containsExactlyInAnyOrder("ms-catalog", "ms-booking", "rabbitmq");
        // De vuelta en el listado de turistas, con su estado.
        assertThat(JsonPath.<List<String>>read(cuerpo(admin(get(BASE + "/turistas?q=" + correo(id)))),
                "$.elementos[*].idUsuario")).containsExactly(id.toString());
    }

    @Test
    void elTokenAnteriorRecibe401SesionInvalidadaYUnLoginNuevoTraeElRolNuevo() throws Exception {
        UUID id = crearTurista("Tk" + marca(), "Activo");
        String viejo = JsonPath.read(cuerpo(login(correo(id))), "$.token");
        assertThat(me(viejo).getResponse().getStatus()).isEqualTo(200);

        cambio(id, "Gestor", "Turista");

        MvcResult rechazado = me(viejo);
        assertThat(rechazado.getResponse().getStatus()).isEqualTo(401);
        assertThat(JsonPath.<String>read(cuerpo(rechazado), "$.codigo")).isEqualTo("SESION_INVALIDADA");
        MvcResult nueva = login(correo(id));
        assertThat(nueva.getResponse().getStatus()).isEqualTo(200);
        String token = JsonPath.read(cuerpo(nueva), "$.token");
        assertThat(SignedJWT.parse(token).getJWTClaimsSet().getStringClaim("rol")).isEqualTo("Gestor");
        assertThat(SignedJWT.parse(token).getJWTClaimsSet().getLongClaim("ver")).isEqualTo(1);
        assertThat(JsonPath.<String>read(cuerpo(me(token)), "$.rol")).isEqualTo("Gestor");
    }

    // ---- 409 --------------------------------------------------------------------------------------------------

    @Test
    void unPrestadorNoEsElegible() throws Exception {
        MvcResult resultado = cambio(ID_PRESTADOR_SEMILLA, "Gestor", "Prestador");
        assertThat(resultado.getResponse().getStatus()).isEqualTo(400); // Prestador no es un rol de la acción

        long antes = version(ID_PRESTADOR_SEMILLA);
        MvcResult conTurista = cambio(ID_PRESTADOR_SEMILLA, "Gestor", "Turista");
        assertThat(conTurista.getResponse().getStatus()).isEqualTo(409);
        assertThat(JsonPath.<String>read(cuerpo(conTurista), "$.codigo")).isEqualTo("CUENTA_NO_ELEGIBLE");
        assertThat(JsonPath.<String>read(cuerpo(conTurista), "$.motivo")).isEqualTo("PRESTADOR");
        assertThat(rol(ID_PRESTADOR_SEMILLA)).isEqualTo("Prestador");
        assertThat(version(ID_PRESTADOR_SEMILLA)).isEqualTo(antes);
    }

    @Test
    void unaCuentaSinFilaDeTuristaSeModificaPeroNoSeRevoca() throws Exception {
        UUID id = crearPersonal(3, "Sg" + marca(), false);

        MvcResult revocar = cambio(id, "Turista", "Gestor");
        assertThat(revocar.getResponse().getStatus()).isEqualTo(409);
        assertThat(JsonPath.<String>read(cuerpo(revocar), "$.codigo")).isEqualTo("CUENTA_NO_ELEGIBLE");
        assertThat(JsonPath.<String>read(cuerpo(revocar), "$.motivo")).isEqualTo("SIN_ROL_BASE");
        assertThat(version(id)).isZero();

        MvcResult modificar = cambio(id, "Administrador", "Gestor");
        assertThat(modificar.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<Boolean>read(cuerpo(modificar), "$.revocable")).isFalse();
        assertThat(JsonPath.<Object>read(cuerpo(modificar), "$.nombre")).isNull();
    }

    @Test
    void unTuristaDesactivadoNoRecibeUnRol() throws Exception {
        UUID id = crearTurista("Ds" + marca(), "Desactivado");

        MvcResult resultado = cambio(id, "Gestor", "Turista");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.motivo")).isEqualTo("DESACTIVADA");
        assertThat(rol(id)).isEqualTo("Turista");
        assertThat(existe("gestor", id)).isFalse();
    }

    @Test
    void elAdministradorNoCambiaSuPropiaCuenta() throws Exception {
        long antes = version(ID_ADMIN);

        MvcResult resultado = cambio(ID_ADMIN, "Gestor", "Administrador");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("CAMBIO_ROL_PROPIO");
        assertThat(rol(ID_ADMIN)).isEqualTo("Administrador");
        assertThat(version(ID_ADMIN)).isEqualTo(antes);
    }

    @Test
    void unRolActualDesactualizadoEs409ConElRolActual() throws Exception {
        UUID id = crearTurista("Ra" + marca(), "Activo");
        cambio(id, "Gestor", "Turista");

        MvcResult resultado = cambio(id, "Administrador", "Turista");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("ROL_CAMBIADO");
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.rolActual")).isEqualTo("Gestor");
        assertThat(version(id)).isEqualTo(1);
    }

    @Test
    void dejarCeroAdministradoresActivosEs409() throws Exception {
        // Quien actúa tiene un token vigente de Administrador, pero su cuenta ya no está Activa: el único activo
        // sería la cuenta degradada.
        UUID actor = crearPersonal(4, "Ac" + marca(), false);
        UUID objetivo = crearPersonal(4, "Ob" + marca(), true);
        String tokenActor = token(actor, Rol.Administrador);
        List<UUID> otros = administradoresActivosSalvo(objetivo);
        try {
            otros.forEach(id -> jdbc.update("UPDATE usuario SET estado_cuenta = 'Inactivo' WHERE id_usuario = ?", id));

            MvcResult resultado = conToken(post(BASE + "/cuentas/" + objetivo + "/cambio-rol")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rolNuevo\":\"Turista\",\"rolActual\":\"Administrador\"}"), tokenActor);

            assertThat(resultado.getResponse().getStatus()).isEqualTo(409);
            assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("ULTIMO_ADMINISTRADOR");
            assertThat(rol(objetivo)).isEqualTo("Administrador");
            assertThat(existe("administrador", objetivo)).isTrue();
            assertThat(version(objetivo)).isZero();
        } finally {
            otros.forEach(id -> jdbc.update("UPDATE usuario SET estado_cuenta = 'Activo' WHERE id_usuario = ?", id));
        }
        assertThat(administradoresActivos()).isGreaterThanOrEqualTo(1);
    }

    /**
     * DEC-106: dos Administradores que se degradan entre sí a la vez. El primero toma el bloqueo, degrada al otro y
     * confirma con una pausa; el segundo espera el bloqueo, PostgreSQL reevalúa el WHERE y ya no ve al primero como
     * Administrador: 409. Los demás Administradores activos se apartan y se restauran aunque falle una aserción.
     */
    @Test
    void dosAdministradoresQueSeDegradanEntreSiALaVezDejanUnoActivo() throws Exception {
        UUID a = crearPersonal(4, "Ca" + marca(), false);
        UUID b = crearPersonal(4, "Cb" + marca(), false);
        List<UUID> otros = administradoresActivosSalvo(a, b);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            otros.forEach(id -> jdbc.update("UPDATE usuario SET estado_cuenta = 'Inactivo' WHERE id_usuario = ?", id));
            CountDownLatch bloqueado = new CountDownLatch(1);

            // A degrada a B: toma el bloqueo, aplica y espera antes de confirmar.
            Future<?> primero = hilos.submit(() -> transaccion.executeWithoutResult(estado -> {
                CambioRol cambio = new CambioRol(b, Rol.Administrador, Rol.Gestor, 0);
                assertThat(repositorio.bloquearAdministradoresActivos()).contains(a, b);
                repositorio.aplicar(cambio);
                bloqueado.countDown();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            // B degrada a A, con el caso de uso transaccional completo.
            Future<?> segundo = hilos.submit(() -> {
                bloqueado.await(10, TimeUnit.SECONDS);
                return cambiarRol.cambiarRol(new ComandoCambioRol(a, "Gestor", "Administrador", b));
            });

            primero.get(30, TimeUnit.SECONDS);
            Throwable causa = null;
            try {
                segundo.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException fallo) {
                causa = fallo.getCause();
            }
            assertThat(causa).isInstanceOf(UltimoAdministradorException.class);
            assertThat(rol(a)).isEqualTo("Administrador");
            assertThat(rol(b)).isEqualTo("Gestor");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE id_usuario = ?",
                    Integer.class, a)).isZero();
        } finally {
            hilos.shutdownNow();
            otros.forEach(id -> jdbc.update("UPDATE usuario SET estado_cuenta = 'Activo' WHERE id_usuario = ?", id));
        }
        assertThat(administradoresActivos()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado_cuenta::text FROM usuario WHERE id_usuario = ?", String.class,
                ID_ADMIN)).isEqualTo("Activo");
    }

    /** Los mismos dos cambios por HTTP y a la vez: cualquiera sea el orden, queda al menos un Administrador. */
    @Test
    void dosDegradacionesSimultaneasPorHttpNuncaDejanCeroAdministradores() throws Exception {
        UUID a = crearPersonal(4, "Ha" + marca(), false);
        UUID b = crearPersonal(4, "Hb" + marca(), false);
        String tokenA = token(a, Rol.Administrador);
        String tokenB = token(b, Rol.Administrador);
        List<UUID> otros = administradoresActivosSalvo(a, b);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            otros.forEach(id -> jdbc.update("UPDATE usuario SET estado_cuenta = 'Inactivo' WHERE id_usuario = ?", id));
            Future<MvcResult> deA = hilos.submit(() -> {
                salida.await();
                return conToken(post(BASE + "/cuentas/" + b + "/cambio-rol").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rolNuevo\":\"Gestor\",\"rolActual\":\"Administrador\"}"), tokenA);
            });
            Future<MvcResult> deB = hilos.submit(() -> {
                salida.await();
                return conToken(post(BASE + "/cuentas/" + a + "/cambio-rol").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rolNuevo\":\"Gestor\",\"rolActual\":\"Administrador\"}"), tokenB);
            });
            salida.countDown();
            List<MvcResult> resultados = List.of(deA.get(30, TimeUnit.SECONDS), deB.get(30, TimeUnit.SECONDS));

            assertThat(resultados.stream().filter(r -> r.getResponse().getStatus() == 200)).hasSize(1);
            MvcResult perdedora = resultados.stream().filter(r -> r.getResponse().getStatus() != 200).findFirst()
                    .orElseThrow();
            // Si llega después de la confirmación, el token de quien perdió el rol ya no vale (401); si llega antes,
            // espera el bloqueo (409).
            assertThat(JsonPath.<String>read(cuerpo(perdedora), "$.codigo"))
                    .isIn("ULTIMO_ADMINISTRADOR", "SESION_INVALIDADA");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE id_usuario IN (?, ?) AND id_rol = 4"
                    + " AND estado_cuenta = 'Activo'", Integer.class, a, b)).isEqualTo(1);
        } finally {
            hilos.shutdownNow();
            otros.forEach(id -> jdbc.update("UPDATE usuario SET estado_cuenta = 'Activo' WHERE id_usuario = ?", id));
        }
        assertThat(administradoresActivos()).isGreaterThanOrEqualTo(1);
    }

    // ---- 400, 404 y atomicidad --------------------------------------------------------------------------------

    @Test
    void unRolNuevoInvalidoOIgualAlActualEs400ConElCampo() throws Exception {
        UUID id = crearTurista("In" + marca(), "Activo");

        for (String[] caso : List.of(new String[]{"{\"rolNuevo\":\"Turista\",\"rolActual\":\"Turista\"}", "rolNuevo",
                        "NO_PERMITIDO"},
                new String[]{"{\"rolNuevo\":\"Jefe\",\"rolActual\":\"Turista\"}", "rolNuevo", "NO_PERMITIDO"},
                new String[]{"{\"rolActual\":\"Turista\"}", "rolNuevo", "REQUERIDO"},
                new String[]{"{\"rolNuevo\":\"Gestor\"}", "rolActual", "REQUERIDO"})) {
            MvcResult resultado = admin(post(BASE + "/cuentas/" + id + "/cambio-rol")
                    .contentType(MediaType.APPLICATION_JSON).content(caso[0]));
            assertThat(resultado.getResponse().getStatus()).as(caso[0]).isEqualTo(400);
            assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("DATOS_INVALIDOS");
            assertThat(JsonPath.<String>read(cuerpo(resultado), "$.campos." + caso[1])).isEqualTo(caso[2]);
        }
        MvcResult sinJson = admin(post(BASE + "/cuentas/" + id + "/cambio-rol")
                .contentType(MediaType.APPLICATION_JSON).content("{"));
        assertThat(sinJson.getResponse().getStatus()).isEqualTo(400);
        assertThat(version(id)).isZero();
    }

    @Test
    void unaCuentaInexistenteEs404() throws Exception {
        MvcResult resultado = cambio(UUID.randomUUID(), "Gestor", "Turista");

        assertThat(resultado.getResponse().getStatus()).isEqualTo(404);
        assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("CUENTA_NO_ENCONTRADA");
    }

    @Test
    void siFallaElEventoNoQuedaNiElRolNiElSubtipoNiLaVersion() throws Exception {
        UUID id = crearTurista("Fa" + marca(), "Activo");
        jdbc.execute("CREATE OR REPLACE FUNCTION fallar_outbox_rol() RETURNS trigger LANGUAGE plpgsql"
                + " AS $$ BEGIN RAISE EXCEPTION 'outbox caído'; END $$");
        jdbc.execute("CREATE TRIGGER tg_fallar_outbox_rol BEFORE INSERT ON outbox_evento"
                + " FOR EACH ROW EXECUTE FUNCTION fallar_outbox_rol()");
        try {
            assertThat(cambio(id, "Administrador", "Turista").getResponse().getStatus()).isEqualTo(500);
        } finally {
            jdbc.execute("DROP TRIGGER tg_fallar_outbox_rol ON outbox_evento");
            jdbc.execute("DROP FUNCTION fallar_outbox_rol()");
        }
        assertThat(rol(id)).isEqualTo("Turista");
        assertThat(existe("administrador", id)).isFalse();
        assertThat(version(id)).isZero();
    }

    // ---- listado del personal ---------------------------------------------------------------------------------

    @Test
    void elListadoDelPersonalFiltraPorRolYTextoYPagina() throws Exception {
        String marca = marca();
        for (int i = 0; i < 11; i++) {
            crearPersonal(3, "Lg" + i + marca, true);
        }
        UUID admin = crearPersonal(4, "La" + marca, false);
        crearTurista("Lt" + marca, "Activo");

        String todos = cuerpo(admin(get(BASE + "/personal?q=" + marca.toLowerCase())));
        assertThat(JsonPath.<Integer>read(todos, "$.totalElementos")).isEqualTo(12);
        assertThat(JsonPath.<Integer>read(todos, "$.totalPaginas")).isEqualTo(2);
        assertThat(JsonPath.<List<Object>>read(todos, "$.elementos")).hasSize(10);

        String segunda = cuerpo(admin(get(BASE + "/personal?q=" + marca + "&pagina=1")));
        assertThat(JsonPath.<List<Object>>read(segunda, "$.elementos")).hasSize(2);

        String administradores = cuerpo(admin(get(BASE + "/personal?rol=Administrador&q=" + marca)));
        assertThat(JsonPath.<List<String>>read(administradores, "$.elementos[*].idUsuario"))
                .containsExactly(admin.toString());
        assertThat(JsonPath.<Boolean>read(administradores, "$.elementos[0].revocable")).isFalse();
        assertThat(JsonPath.<String>read(administradores, "$.elementos[0].rol")).isEqualTo("Administrador");

        String porNombre = cuerpo(admin(get(BASE + "/personal?rol=Gestor&q=NombreLg3" + marca)));
        assertThat(JsonPath.<Integer>read(porNombre, "$.totalElementos")).isEqualTo(1);
        assertThat(JsonPath.<Boolean>read(porNombre, "$.elementos[0].revocable")).isTrue();
        assertThat(JsonPath.<String>read(porNombre, "$.elementos[0].correo"))
                .isEqualTo(("Lg3" + marca).toLowerCase() + "@mail.test");

        MvcResult rolInvalido = admin(get(BASE + "/personal?rol=Turista"));
        assertThat(rolInvalido.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonPath.<String>read(cuerpo(rolInvalido), "$.campos.rol")).isEqualTo("NO_PERMITIDO");
    }

    // ---- seguridad y rutas de turistas ------------------------------------------------------------------------

    @Test
    void sinTokenEs401YOtroRolEs403EnLasRutasNuevas() throws Exception {
        UUID cualquiera = UUID.randomUUID();
        List<MockHttpServletRequestBuilder> rutas = List.of(get(BASE + "/personal"),
                post(BASE + "/cuentas/" + cualquiera + "/cambio-rol").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rolNuevo\":\"Gestor\",\"rolActual\":\"Turista\"}"));
        for (MockHttpServletRequestBuilder ruta : rutas) {
            MvcResult sinToken = mvc.perform(ruta).andReturn();
            assertThat(sinToken.getResponse().getStatus()).isEqualTo(401);
            assertThat(JsonPath.<String>read(cuerpo(sinToken), "$.codigo")).isEqualTo("NO_AUTENTICADO");
        }
        for (Rol rol : List.of(Rol.Turista, Rol.Prestador, Rol.Gestor)) {
            UUID id = crearUsuario(rol.ordinal() + 1, rol.name().toLowerCase() + marca() + "@mail.test", "Activo");
            for (MockHttpServletRequestBuilder ruta : List.of(get(BASE + "/personal"),
                    post(BASE + "/cuentas/" + cualquiera + "/cambio-rol").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rolNuevo\":\"Gestor\",\"rolActual\":\"Turista\"}"))) {
                MvcResult resultado = conToken(ruta, token(id, rol));
                assertThat(resultado.getResponse().getStatus()).as(rol.name()).isEqualTo(403);
                assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("ACCESO_DENEGADO");
            }
        }
    }

    @Test
    void unGestorConFilaDeTuristaNoApareceEnTuristasNiSeDesactivaPorEsaRuta() throws Exception {
        UUID id = crearTurista("Gt" + marca(), "Activo");
        cambio(id, "Gestor", "Turista");

        String turistas = cuerpo(admin(get(BASE + "/turistas?q=" + correo(id))));
        assertThat(JsonPath.<Integer>read(turistas, "$.totalElementos")).isZero();
        for (String accion : List.of("desactivacion", "reactivacion")) {
            MvcResult resultado = admin(post(BASE + "/turistas/" + id + "/" + accion));
            assertThat(resultado.getResponse().getStatus()).as(accion).isEqualTo(404);
            assertThat(JsonPath.<String>read(cuerpo(resultado), "$.codigo")).isEqualTo("CUENTA_NO_ENCONTRADA");
        }
        assertThat(jdbc.queryForObject("SELECT estado_cuenta::text FROM usuario WHERE id_usuario = ?", String.class, id))
                .isEqualTo("Activo");
    }

    // ---- ayudas -----------------------------------------------------------------------------------------------

    private static String marca() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private MvcResult cambio(UUID id, String rolNuevo, String rolActual) throws Exception {
        return admin(post(BASE + "/cuentas/" + id + "/cambio-rol").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rolNuevo\":\"" + rolNuevo + "\",\"rolActual\":\"" + rolActual + "\"}"));
    }

    /** El Administrador de la semilla, con su versión actual (V15 la subió a 1). */
    private MvcResult admin(MockHttpServletRequestBuilder solicitud) throws Exception {
        return conToken(solicitud, token(ID_ADMIN, Rol.Administrador));
    }

    private MvcResult conToken(MockHttpServletRequestBuilder solicitud, String token) throws Exception {
        return mvc.perform(solicitud.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn();
    }

    private String token(UUID id, Rol rol) {
        return tokens.emitir(new Usuario(id, "x@samaria360.test", "x", rol, EstadoCuenta.Activo, 0, null, version(id)),
                Instant.now()).token();
    }

    private MvcResult me(String token) throws Exception {
        return conToken(get("/api/v1/auth/me"), token);
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

    private String rol(UUID id) {
        return jdbc.queryForObject("SELECT r.nombre FROM usuario u JOIN rol r ON r.id_rol = u.id_rol"
                + " WHERE u.id_usuario = ?", String.class, id);
    }

    private String correo(UUID id) {
        return jdbc.queryForObject("SELECT correo FROM usuario WHERE id_usuario = ?", String.class, id);
    }

    private boolean existe(String tabla, UUID id) {
        Integer filas = jdbc.queryForObject("SELECT count(*) FROM " + tabla + " WHERE id_usuario = ?", Integer.class,
                id);
        return filas != null && filas > 0;
    }

    private Map<String, Object> evento(UUID id, long version) {
        String carga = jdbc.queryForObject("SELECT carga::text FROM outbox_evento WHERE id_usuario = ?"
                + " AND tipo = 'CuentaActualizada' AND (carga->>'version')::bigint = ?", String.class, id, version);
        return JsonPath.read(carga, "$");
    }

    private List<String> destinos(UUID id) {
        return jdbc.queryForList("SELECT en.destino FROM outbox_entrega en JOIN outbox_evento e"
                + " ON e.id_evento = en.id_evento WHERE e.id_usuario = ? AND en.destino <> 'rabbitmq'", String.class, id);
    }

    private int administradoresActivos() {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM usuario WHERE id_rol = 4 AND estado_cuenta = 'Activo'",
                Integer.class);
        return total == null ? 0 : total;
    }

    private List<UUID> administradoresActivosSalvo(UUID... excluidos) {
        List<UUID> fuera = List.of(excluidos);
        return jdbc.queryForList("SELECT id_usuario FROM usuario WHERE id_rol = 4 AND estado_cuenta = 'Activo'",
                UUID.class).stream().filter(id -> !fuera.contains(id)).toList();
    }

    private UUID crearUsuario(int idRol, String correo, String estadoCuenta) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id_usuario, id_rol, correo, contrasena_hash, fecha_registro,"
                + " intentos_fallidos, estado_cuenta) VALUES (?, ?, ?, ?, CURRENT_DATE, 0, ?::estado_cuenta)",
                id, idRol, correo, hasher.codificar(CONTRASENA), estadoCuenta);
        return id;
    }

    private UUID crearTurista(String nombre, String cuenta) {
        UUID id = crearUsuario(1, nombre.toLowerCase() + "@mail.test", cuenta);
        filaTurista(id, nombre);
        return id;
    }

    /** Gestor (3) o Administrador (4) con su subtipo y, si {@code conTurista}, con fila en {@code turista}. */
    private UUID crearPersonal(int idRol, String nombre, boolean conTurista) {
        UUID id = crearUsuario(idRol, nombre.toLowerCase() + "@mail.test", "Activo");
        jdbc.update("INSERT INTO " + (idRol == 3 ? "gestor" : "administrador") + " (id_usuario) VALUES (?)", id);
        if (conTurista) {
            filaTurista(id, nombre);
        }
        return id;
    }

    private void filaTurista(UUID id, String nombre) {
        jdbc.update("INSERT INTO turista (id_usuario, primer_nombre, primer_apellido, pais_origen, acepto_politicas)"
                + " VALUES (?, ?, 'Apellido', 'CO', TRUE)", id, "Nombre" + nombre);
    }

    private static String cuerpo(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
