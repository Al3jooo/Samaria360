package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoAdministrativo;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoCuenta;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoValidacion;
import com.jayway.jsonpath.JsonPath;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V17 (DEC-147): encola un evento {@code CuentaActualizada} con el RNT por cada prestador, en cualquier estado, y sube
 * su versión, para que ms-catalog tenga el RNT en su proyección (DEC-126, HC2). Cada prueba usa su propio PostgreSQL: un
 * volumen nuevo o una base que ya estaba en V16.
 */
class MigracionesRntIntegracionTest {

    private static final String IMAGEN = "postgres:16-alpine";

    @Test
    void v17_enUnVolumenNuevoConSemillaEncolaUnEventoConRntPorCadaPrestadorYSubeSuVersion() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(IMAGEN)) {
            postgres.start();
            assertThat(flyway(postgres, "16", true).migrate().success).isTrue();
            JdbcTemplate jdbc = jdbc(postgres);
            Map<UUID, Long> versionesAntes = versionesDe(jdbc, "Prestador");
            Map<UUID, Long> otrasAntes = versionesDeOtrosRoles(jdbc);
            int eventosAntes = jdbc.queryForObject("SELECT count(*) FROM outbox_evento", Integer.class);

            assertThat(flyway(postgres, "17", true).migrate().success).isTrue();

            // prestador@ (Activo), prestador2@ (Inactivo), prestador3@ y prestador4@ (Pendientes) de la semilla.
            assertThat(versionesAntes).hasSize(4);
            List<Map<String, Object>> eventos = eventosDePrestadores(jdbc);
            assertThat(eventos).hasSize(4);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento", Integer.class))
                    .isEqualTo(eventosAntes + 4);
            for (Map<String, Object> evento : eventos) {
                UUID id = (UUID) evento.get("id_usuario");
                String carga = (String) evento.get("carga");
                Map<String, Object> prestador = jdbc.queryForMap("SELECT p.rnt, u.version_cuenta FROM prestador p"
                        + " JOIN usuario u ON u.id_usuario = p.id_usuario WHERE p.id_usuario = ?", id);
                assertThat(evento.get("tipo")).isEqualTo("CuentaActualizada");
                assertThat(JsonPath.<String>read(carga, "$.idUsuario")).isEqualTo(id.toString());
                assertThat(JsonPath.<String>read(carga, "$.rol")).isEqualTo("Prestador");
                assertThat(JsonPath.<String>read(carga, "$.rnt")).isEqualTo(prestador.get("rnt"));
                assertThat(((Number) prestador.get("version_cuenta")).longValue()).isEqualTo(versionesAntes.get(id) + 1);
                assertThat(JsonPath.<Integer>read(carga, "$.version").longValue()).isEqualTo(versionesAntes.get(id) + 1);
                assertThat(jdbc.queryForList("SELECT en.destino FROM outbox_entrega en WHERE en.id_evento = ?"
                        + " AND en.entregado_en IS NULL", String.class, evento.get("id_evento")))
                        .containsExactlyInAnyOrder("ms-catalog", "ms-booking");
            }
            // Los demás roles no cambian de versión.
            assertThat(versionesDeOtrosRoles(jdbc)).isEqualTo(otrasAntes);
        }
    }

    /**
     * Ajuste 4 de la aprobación: el {@code estadoCuenta} que escribe V17 en SQL es, para cada prestador, el mismo que
     * calcula {@link EstadoAdministrativo#dePrestador} en el dominio (DEC-43).
     */
    @Test
    void v17_elEstadoDeCadaEventoEsElDeEstadoAdministrativoDePrestador() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(IMAGEN)) {
            postgres.start();
            assertThat(flyway(postgres, "17", true).migrate().success).isTrue();
            JdbcTemplate jdbc = jdbc(postgres);

            List<Map<String, Object>> eventos = eventosDePrestadores(jdbc);
            assertThat(eventos).isNotEmpty();
            for (Map<String, Object> evento : eventos) {
                assertThat(JsonPath.<String>read((String) evento.get("carga"), "$.estadoCuenta"))
                        .as("prestador %s", evento.get("id_usuario"))
                        .isEqualTo(estadoDelDominio(jdbc, (UUID) evento.get("id_usuario")).name());
            }
            // La semilla cubre Activo, Inactivo y Pendiente.
            assertThat(eventos).extracting(e -> JsonPath.<String>read((String) e.get("carga"), "$.estadoCuenta"))
                    .contains("Activo", "Inactivo", "Pendiente");
        }
    }

    @Test
    void v17_sinSemillaYSinPrestadoresNoEncolaNada() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(IMAGEN)) {
            postgres.start();
            assertThat(flyway(postgres, "17", false).migrate().success).isTrue();

            assertThat(jdbc(postgres).queryForObject("SELECT count(*) FROM outbox_evento", Integer.class)).isZero();
        }
    }

    /**
     * Una base que ya estaba en V16, con un prestador Rechazado que cambió de estado antes (versión 5) y otro validado
     * con la cuenta Inactiva: V17 los encola con la versión siguiente, su RNT y el estado derivado.
     */
    @Test
    void v17_sobreUnaBaseExistenteSubeLaVersionDeCadaPrestadorConSuEstadoDerivado() {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(IMAGEN)) {
            postgres.start();
            assertThat(flyway(postgres, "16", true).migrate().success).isTrue();
            JdbcTemplate jdbc = jdbc(postgres);
            UUID rechazado = crearPrestador(jdbc, "rechazado@hotel.test", "RNT-RECH", "Rechazado", "Activo", 5);
            UUID inactivo = crearPrestador(jdbc, "inactivo@hotel.test", "RNT-INAC", "Activo", "Inactivo", 2);

            assertThat(flyway(postgres, "17", true).migrate().success).isTrue();

            for (Object[] esperado : new Object[][]{{rechazado, "RNT-RECH", "Rechazado", 6}, {inactivo, "RNT-INAC",
                    "Inactivo", 3}}) {
                UUID id = (UUID) esperado[0];
                String carga = jdbc.queryForObject("SELECT carga::text FROM outbox_evento WHERE id_usuario = ?",
                        String.class, id);
                assertThat(JsonPath.<String>read(carga, "$.rnt")).isEqualTo(esperado[1]);
                assertThat(JsonPath.<String>read(carga, "$.estadoCuenta")).isEqualTo(esperado[2])
                        .isEqualTo(estadoDelDominio(jdbc, id).name());
                assertThat(JsonPath.<Integer>read(carga, "$.version")).isEqualTo(esperado[3]);
                assertThat(jdbc.queryForObject("SELECT version_cuenta FROM usuario WHERE id_usuario = ?", Long.class,
                        id)).isEqualTo(((Integer) esperado[3]).longValue());
            }
            assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success"
                    + " ORDER BY installed_rank", String.class)).endsWith("16", "17");
        }
    }

    private static UUID crearPrestador(JdbcTemplate jdbc, String correo, String rnt, String validacion, String cuenta,
                                       long version) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id_usuario, id_rol, correo, contrasena_hash, fecha_registro,"
                + " intentos_fallidos, estado_cuenta, version_cuenta) VALUES (?, 2, ?, 'x', CURRENT_DATE, 0,"
                + " ?::estado_cuenta, ?)", id, correo, cuenta, version);
        jdbc.update("INSERT INTO prestador (id_usuario, rnt, telefono, nombre_representante_legal,"
                + " correo_corporativo, estado_validacion, acepto_politicas, motivo_rechazo, revisado_en)"
                + " VALUES (?, ?, '1', 'Rep', ?, ?, TRUE, ?, now())", id, rnt, correo, validacion,
                "Rechazado".equals(validacion) ? "RNT no vigente" : null);
        return id;
    }

    private static EstadoAdministrativo estadoDelDominio(JdbcTemplate jdbc, UUID id) {
        Map<String, Object> fila = jdbc.queryForMap("SELECT p.estado_validacion, u.estado_cuenta::text AS cuenta"
                + " FROM prestador p JOIN usuario u ON u.id_usuario = p.id_usuario WHERE p.id_usuario = ?", id);
        return EstadoAdministrativo.dePrestador(EstadoValidacion.desdeTexto((String) fila.get("estado_validacion")),
                EstadoCuenta.valueOf((String) fila.get("cuenta")));
    }

    private static List<Map<String, Object>> eventosDePrestadores(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT id_evento, id_usuario, tipo, carga::text AS carga FROM outbox_evento"
                + " WHERE carga->>'rol' = 'Prestador'");
    }

    private static Map<UUID, Long> versionesDe(JdbcTemplate jdbc, String rol) {
        Map<UUID, Long> versiones = new java.util.HashMap<>();
        jdbc.query("SELECT u.id_usuario, u.version_cuenta FROM usuario u JOIN rol r ON r.id_rol = u.id_rol"
                + " WHERE r.nombre = ?", rs -> {
            versiones.put(rs.getObject("id_usuario", UUID.class), rs.getLong("version_cuenta"));
        }, rol);
        return versiones;
    }

    private static Map<UUID, Long> versionesDeOtrosRoles(JdbcTemplate jdbc) {
        Map<UUID, Long> versiones = new java.util.HashMap<>();
        for (String rol : List.of("Turista", "Gestor", "Administrador")) {
            versiones.putAll(versionesDe(jdbc, rol));
        }
        return versiones;
    }

    private static Flyway flyway(PostgreSQLContainer<?> postgres, String objetivo, boolean conSemilla) {
        var configuracion = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(conSemilla ? new String[]{"classpath:db/migration", "classpath:db/semilla"}
                        : new String[]{"classpath:db/migration"});
        if (objetivo != null) {
            configuracion.target(objetivo);
        }
        return configuracion.load();
    }

    private static JdbcTemplate jdbc(PostgreSQLContainer<?> postgres) {
        return new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()));
    }
}
