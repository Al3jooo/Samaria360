package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.auth.infrastructure.PruebaIntegracion;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Migraciones de «Administrar cuentas»: V11 (revisión del prestador), V12 (versión), V13 (outbox) y V14 (semilla). */
@PruebaIntegracion
class MigracionesAdministrarCuentasIntegracionTest {

    private static final String MOTIVO_ANTERIOR = "Rechazo anterior a RF28, sin justificación registrada";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayAplicaDeV1AV16SobreLaSemillaCompleta() {
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);

        assertThat(versiones).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19");
    }

    @Test
    void v11_unRechazadoExigeSuJustificacionDeComoMucho500Caracteres() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id_usuario, id_rol, correo, contrasena_hash, fecha_registro,"
                + " intentos_fallidos, estado_cuenta) VALUES (?, 2, ?, 'x', CURRENT_DATE, 0, 'Activo')",
                id, "v11-" + id + "@hotel.test");
        jdbc.update("INSERT INTO prestador (id_usuario, rnt, telefono, nombre_representante_legal,"
                + " correo_corporativo, estado_validacion, acepto_politicas)"
                + " VALUES (?, ?, '1', 'Ana', 'a@b.test', 'Pendiente', TRUE)", id, "RNT-" + id);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE prestador SET estado_validacion = 'Rechazado' WHERE id_usuario = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_prestador_rechazo_con_motivo");
        assertThatThrownBy(() -> jdbc.update("UPDATE prestador SET estado_validacion = 'Rechazado',"
                + " motivo_rechazo = ? WHERE id_usuario = ?", "a".repeat(501), id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("UPDATE prestador SET estado_validacion = 'Rechazado', motivo_rechazo = ?"
                + " WHERE id_usuario = ?", "a".repeat(500), id)).isEqualTo(1);
    }

    @Test
    void v12_laVersionDeLaCuentaEsObligatoriaYParteDeCero() {
        assertThat(jdbc.queryForMap("SELECT is_nullable, data_type FROM information_schema.columns"
                + " WHERE table_name = 'usuario' AND column_name = 'version_cuenta'"))
                .containsEntry("is_nullable", "NO").containsEntry("data_type", "bigint");
        assertThat(jdbc.queryForObject("SELECT version_cuenta FROM usuario WHERE correo = 'turista@samaria360.test'",
                Long.class)).isZero();
        assertThatThrownBy(() -> jdbc.update("UPDATE usuario SET version_cuenta = -1 WHERE correo = 'turista@samaria360.test'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v13_elOutboxSoloAceptaElEventoYLosDestinosConocidos() {
        UUID usuario = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario (id_usuario, id_rol, correo, contrasena_hash, fecha_registro,"
                + " intentos_fallidos, estado_cuenta) VALUES (?, 1, ?, 'x', CURRENT_DATE, 0, 'Activo')",
                usuario, "v13-" + usuario + "@mail.test");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO outbox_evento (tipo, id_usuario, carga, creado_en)"
                + " VALUES ('Otro', ?, '{}'::jsonb, now())", usuario))
                .isInstanceOf(DataIntegrityViolationException.class);
        Long evento = jdbc.queryForObject("INSERT INTO outbox_evento (tipo, id_usuario, carga, creado_en)"
                + " VALUES ('CuentaActualizada', ?, '{}'::jsonb, now()) RETURNING id_evento", Long.class, usuario);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO outbox_entrega (id_evento, destino, proximo_intento)"
                + " VALUES (?, 'ms-otro', now())", evento)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("INSERT INTO outbox_entrega (id_evento, destino, proximo_intento)"
                + " VALUES (?, 'ms-catalog', now())", evento)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO outbox_entrega (id_evento, destino, proximo_intento)"
                + " VALUES (?, 'ms-catalog', now())", evento)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v14_laSemillaTrae_dosPrestadoresPendientesYUnTuristaMas() {
        assertThat(jdbc.queryForList("SELECT correo_corporativo FROM prestador WHERE estado_validacion = 'Pendiente'"
                + " AND rnt IN ('RNT-00002', 'RNT-00003') ORDER BY rnt", String.class))
                .containsExactly("contacto@kayaktaganga.test", "contacto@hostalrodadero.test");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turista WHERE primer_nombre = 'Marta'"
                + " AND primer_apellido = 'Lee' AND pais_origen = 'US'", Integer.class)).isEqualTo(1);
    }

    /**
     * La razón de que V11 rellene antes del CHECK: una base de desarrollo con un prestador Rechazado de antes de RF28
     * haría fallar la migración. Se aplica hasta V10, se inserta ese caso y se migra el resto.
     */
    @Test
    void v11_rellenaElMotivoDeLosRechazadosAnterioresAntesDeCrearElCheck() throws SQLException {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            Flyway hastaV10 = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration", "classpath:db/semilla").target("10").load();
            hastaV10.migrate();

            UUID id = UUID.randomUUID();
            try (Connection conexion = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                    postgres.getPassword()); var sentencia = conexion.createStatement()) {
                sentencia.executeUpdate("INSERT INTO usuario (id_usuario, id_rol, correo, contrasena_hash,"
                        + " fecha_registro, intentos_fallidos, estado_cuenta) VALUES ('" + id + "', 2, 'viejo@hotel.test',"
                        + " 'x', CURRENT_DATE, 0, 'Activo')");
                sentencia.executeUpdate("INSERT INTO prestador (id_usuario, rnt, telefono, nombre_representante_legal,"
                        + " correo_corporativo, estado_validacion, acepto_politicas) VALUES ('" + id + "', 'RNT-VIEJO',"
                        + " '1', 'Viejo', 'viejo@hotel.test', 'Rechazado', TRUE)");
            }

            Flyway completo = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration", "classpath:db/semilla").load();
            assertThat(completo.migrate().success).isTrue();

            try (Connection conexion = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                    postgres.getPassword()); var sentencia = conexion.createStatement();
                 var resultado = sentencia.executeQuery("SELECT motivo_rechazo FROM prestador WHERE id_usuario = '"
                         + id + "'")) {
                assertThat(resultado.next()).isTrue();
                assertThat(resultado.getString(1)).isEqualTo(MOTIVO_ANTERIOR);
            }
        }
    }
}
