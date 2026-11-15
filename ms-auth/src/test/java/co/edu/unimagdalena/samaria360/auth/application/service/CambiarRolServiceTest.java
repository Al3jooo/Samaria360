package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.fake.CambioRolRepositoryEnMemoria;
import co.edu.unimagdalena.samaria360.auth.application.fake.OutboxEnMemoria;
import co.edu.unimagdalena.samaria360.auth.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoCambioRol;
import co.edu.unimagdalena.samaria360.auth.application.port.in.PersonalResumen;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CambioRolPropioException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaNoElegibleException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaNoEncontradaException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.DatosRegistroInvalidosException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.RolCambiadoException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.UltimoAdministradorException;
import co.edu.unimagdalena.samaria360.auth.domain.model.CuentaConRol;
import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoAdministrativo;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoCuenta;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoCuentaActualizada;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoRolCambiado;
import co.edu.unimagdalena.samaria360.auth.domain.model.MotivoInvalido;
import co.edu.unimagdalena.samaria360.auth.domain.model.MotivoNoElegible;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import co.edu.unimagdalena.samaria360.auth.domain.service.PoliticaAdministradores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Caso de uso Cambiar rol de cuenta con dobles de los puertos (RF45). */
class CambiarRolServiceTest {

    private static final Instant T0 = Instant.parse("2030-01-10T15:00:00Z");
    private static final UUID CUENTA = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");

    private final CambioRolRepositoryEnMemoria cuentas = new CambioRolRepositoryEnMemoria();
    private final OutboxEnMemoria outbox = new OutboxEnMemoria();
    private final CambiarRolService servicio =
            new CambiarRolService(cuentas, outbox, new RelojFijo(T0), new PoliticaAdministradores());

    private void cuenta(Rol rol, EstadoCuenta estado, boolean rolBase, long version) {
        cuentas.agregar(new CuentaConRol(CUENTA, rol, estado, rolBase, version));
    }

    private void administradorQueActua() {
        cuentas.agregar(new CuentaConRol(ADMIN, Rol.Administrador, EstadoCuenta.Activo, false, 1));
    }

    private PersonalResumen cambiar(String rolNuevo, String rolActual) {
        return servicio.cambiarRol(new ComandoCambioRol(CUENTA, rolNuevo, rolActual, ADMIN));
    }

    @Test
    void asignarGestorCambiaElRolSubeLaVersionYRegistraElEventoParaLosDosDestinos() {
        cuenta(Rol.Turista, EstadoCuenta.Activo, true, 2);

        PersonalResumen resumen = cambiar("Gestor", "Turista");

        assertThat(resumen.rol()).isEqualTo(Rol.Gestor);
        assertThat(resumen.revocable()).isTrue();
        assertThat(cuentas.persistida(CUENTA).getVersion()).isEqualTo(3);
        assertThat(outbox.eventos()).singleElement().isEqualTo(
                new EventoCuentaActualizada(CUENTA, Rol.Gestor, EstadoAdministrativo.Activo, 3, null, T0));
        assertThat(outbox.entregas()).extracting(e -> e.destino)
                .containsExactlyInAnyOrder(Destino.MS_CATALOG, Destino.MS_BOOKING);
        // Auditoría de RF13 en RabbitMQ (ADR-011).
        assertThat(outbox.publicables()).singleElement().isInstanceOfSatisfying(EventoRolCambiado.class, evento -> {
            assertThat(evento.idUsuario()).isEqualTo(CUENTA);
            assertThat(evento.rolAnterior()).isEqualTo(Rol.Turista);
            assertThat(evento.rolNuevo()).isEqualTo(Rol.Gestor);
        });
        // Turista → Gestor no quita un Administrador: no se bloquea a nadie.
        assertThat(cuentas.llamadas()).containsExactly("buscar", "aplicar", "resumen");
    }

    @Test
    void revocarAUnAdministradorBloqueaALosActivosYPublicaElRolTurista() {
        administradorQueActua();
        cuenta(Rol.Administrador, EstadoCuenta.Activo, true, 5);

        PersonalResumen resumen = cambiar("Turista", "Administrador");

        assertThat(resumen.rol()).isEqualTo(Rol.Turista);
        assertThat(cuentas.llamadas()).containsExactly("buscar", "bloquear", "aplicar", "resumen");
        assertThat(outbox.eventos()).singleElement().satisfies(evento -> {
            assertThat(evento.rol()).isEqualTo(Rol.Turista);
            assertThat(evento.version()).isEqualTo(6);
        });
    }

    @Test
    void degradarAlUltimoAdministradorActivoEs409SinCambiosNiEvento() {
        cuenta(Rol.Administrador, EstadoCuenta.Activo, true, 5);

        assertThatThrownBy(() -> cambiar("Gestor", "Administrador")).isInstanceOf(UltimoAdministradorException.class);
        assertThat(cuentas.persistida(CUENTA).getRol()).isEqualTo(Rol.Administrador);
        assertThat(outbox.eventos()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "null, Turista, rolNuevo, REQUERIDO",
            "'  ', Turista, rolNuevo, REQUERIDO",
            "Prestador, Turista, rolNuevo, NO_PERMITIDO",
            "Superusuario, Turista, rolNuevo, NO_PERMITIDO",
            "Gestor, null, rolActual, REQUERIDO",
            "Gestor, Prestador, rolActual, NO_PERMITIDO",
            "Gestor, Gestor, rolNuevo, NO_PERMITIDO"})
    void unRolInvalidoOIgualAlActualEs400SinBuscarLaCuenta(String rolNuevo, String rolActual, String campo,
                                                          MotivoInvalido motivo) {
        assertThatThrownBy(() -> cambiar(rolNuevo, rolActual))
                .isInstanceOfSatisfying(DatosRegistroInvalidosException.class,
                        e -> assertThat(e.getCampos()).containsEntry(campo, motivo));
        assertThat(cuentas.llamadas()).isEmpty();
    }

    @Test
    void unaCuentaInexistenteEs404() {
        assertThatThrownBy(() -> cambiar("Gestor", "Turista")).isInstanceOf(CuentaNoEncontradaException.class);
    }

    @Test
    void lasReglasDeLaCuentaSeAplicanAntesDeEscribir() {
        cuenta(Rol.Prestador, EstadoCuenta.Activo, false, 1);
        assertThatThrownBy(() -> cambiar("Gestor", "Turista"))
                .isInstanceOfSatisfying(CuentaNoElegibleException.class,
                        e -> assertThat(e.getMotivo()).isEqualTo(MotivoNoElegible.PRESTADOR));

        cuenta(Rol.Gestor, EstadoCuenta.Activo, true, 1);
        assertThatThrownBy(() -> cambiar("Administrador", "Turista"))
                .isInstanceOfSatisfying(RolCambiadoException.class,
                        e -> assertThat(e.getRolActual()).isEqualTo(Rol.Gestor));

        cuenta(Rol.Gestor, EstadoCuenta.Activo, false, 1);
        assertThatThrownBy(() -> cambiar("Turista", "Gestor"))
                .isInstanceOfSatisfying(CuentaNoElegibleException.class,
                        e -> assertThat(e.getMotivo()).isEqualTo(MotivoNoElegible.SIN_ROL_BASE));

        assertThat(cuentas.llamadas()).doesNotContain("aplicar");
        assertThat(outbox.eventos()).isEmpty();
    }

    @Test
    void elAdministradorNoCambiaSuPropiaCuenta() {
        administradorQueActua();

        assertThatThrownBy(() -> servicio.cambiarRol(new ComandoCambioRol(ADMIN, "Gestor", "Administrador", ADMIN)))
                .isInstanceOf(CambioRolPropioException.class);
        assertThat(cuentas.persistida(ADMIN).getRol()).isEqualTo(Rol.Administrador);
    }

    @Test
    void siFallaElEventoElErrorLlegaAlDecoradorParaRevertir() {
        cuenta(Rol.Turista, EstadoCuenta.Activo, true, 0);
        outbox.fallarAlRegistrar(new IllegalStateException("outbox caído"));

        // En memoria no hay transacción: el servicio solo propaga el error. La reversión la prueba la integración.
        assertThatThrownBy(() -> cambiar("Gestor", "Turista")).isInstanceOf(IllegalStateException.class);
        assertThat(outbox.eventos()).isEmpty();
    }
}
