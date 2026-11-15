package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoCuenta;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import co.edu.unimagdalena.samaria360.auth.domain.model.Usuario;
import co.edu.unimagdalena.samaria360.auth.infrastructure.PruebaIntegracion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PruebaIntegracion
class UsuarioPersistenceAdapterTest {

    private static final UUID ID_TURISTA = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID ID_PRESTADOR = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID ID_GESTOR = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID ID_ADMIN = UUID.fromString("00000000-0000-4000-8000-000000000004");

    @Autowired
    private UsuarioPersistenceAdapter adaptador;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaccion;

    @BeforeEach
    void restablecerEstadoDeLogin() {
        jdbc.update("UPDATE usuario SET intentos_fallidos = 0, bloqueado_hasta = NULL, estado_cuenta = 'Activo'"
                + " WHERE correo <> 'prestador2@samaria360.test'"); // prestador2 es Inactivo por semilla (V3)
    }

    @Test
    void flywayAplicaElEsquemaYLaSemillaYValidateNoFalla() {
        // Si ddl-auto=validate fallara, el contexto no habría arrancado.
        assertThat(jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class))
                // V2, V3, V6, V7 y V14 son de la semilla (perfiles dev y test); las demás, del esquema.
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19");
    }

    @Test
    void buscarPorCorreoEncuentraAlTuristaConSuRolYSuEstado() {
        Optional<Usuario> usuario = transaccion.execute(estado -> adaptador.buscarPorCorreo("turista@samaria360.test"));

        assertThat(usuario).hasValueSatisfying(u -> {
            assertThat(u.getId()).isEqualTo(ID_TURISTA);
            assertThat(u.getRol()).isEqualTo(Rol.Turista);
            assertThat(u.getEstadoCuenta()).isEqualTo(EstadoCuenta.Activo);
            assertThat(u.getIntentosFallidos()).isZero();
            assertThat(u.getBloqueadoHasta()).isEmpty();
            assertThat(u.getContrasenaHash()).startsWith("$2a$10$");
        });
    }

    @Test
    void buscarPorCorreoSinTransaccionFallaDeInmediato() {
        assertThatThrownBy(() -> adaptador.buscarPorCorreo("turista@samaria360.test"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void buscarPorIdFuncionaSinTransaccion() {
        assertThat(adaptador.buscarPorId(ID_ADMIN))
                .hasValueSatisfying(u -> {
                    assertThat(u.getCorreo()).isEqualTo("admin@samaria360.test");
                    assertThat(u.getRol()).isEqualTo(Rol.Administrador);
                });
        assertThat(adaptador.buscarPorId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void buscarNombreVisibleSegunElSubtipo() {
        assertThat(adaptador.buscarNombreVisible(ID_TURISTA)).contains("Ana Pérez");
        assertThat(adaptador.buscarNombreVisible(ID_PRESTADOR)).contains("Carlos Gómez");
        assertThat(adaptador.buscarNombreVisible(ID_GESTOR)).isEmpty();
        assertThat(adaptador.buscarNombreVisible(ID_ADMIN)).isEmpty();
        assertThat(adaptador.buscarNombreVisible(UUID.randomUUID())).isEmpty();
    }
}
