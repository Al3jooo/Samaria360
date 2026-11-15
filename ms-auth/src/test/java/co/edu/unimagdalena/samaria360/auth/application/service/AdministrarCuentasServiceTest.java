package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.fake.CuentasAdminEnMemoria;
import co.edu.unimagdalena.samaria360.auth.application.fake.OutboxEnMemoria;
import co.edu.unimagdalena.samaria360.auth.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoValidacion;
import co.edu.unimagdalena.samaria360.auth.application.port.in.PrestadorDetalle;
import co.edu.unimagdalena.samaria360.auth.application.port.in.TuristaResumen;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaNoEncontradaException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.DatosRegistroInvalidosException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.EstadoCambiadoException;
import co.edu.unimagdalena.samaria360.auth.domain.model.CuentaAdministrada;
import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoAdministrativo;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoCuenta;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoValidacion;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoCuentaActualizada;
import co.edu.unimagdalena.samaria360.auth.domain.model.EventoPrestadorValidado;
import co.edu.unimagdalena.samaria360.auth.domain.model.MotivoInvalido;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Caso de uso Administrar cuentas con dobles de los puertos (RF28, RF22, RF62). */
class AdministrarCuentasServiceTest {

    private static final Instant T0 = Instant.parse("2030-01-10T15:00:00Z");
    private static final UUID PRESTADOR = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
    private static final UUID TURISTA = UUID.fromString("00000000-0000-4000-8000-0000000000a2");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");
    private static final String RNT = "RNT-00042";

    private final CuentasAdminEnMemoria cuentas = new CuentasAdminEnMemoria();
    private final OutboxEnMemoria outbox = new OutboxEnMemoria();
    private final AdministrarCuentasService servicio =
            new AdministrarCuentasService(cuentas, outbox, new RelojFijo(T0));

    private void prestador(EstadoValidacion validacion, EstadoCuenta cuenta, long version) {
        cuentas.agregar(new CuentaAdministrada(PRESTADOR, Rol.Prestador, cuenta, validacion, version, RNT));
    }

    private void turista(EstadoCuenta cuenta, long version) {
        cuentas.agregar(new CuentaAdministrada(TURISTA, Rol.Turista, cuenta, null, version, null));
    }

    @Test
    void aprobarCambiaElEstadoYRegistraElEventoParaLosDosDestinosEnLaMismaOperacion() {
        prestador(EstadoValidacion.Pendiente, EstadoCuenta.Activo, 0);

        PrestadorDetalle detalle = servicio.validar(new ComandoValidacion(PRESTADOR, "Aprobar", null, ADMIN));

        assertThat(detalle.estado()).isEqualTo(EstadoAdministrativo.Activo);
        assertThat(cuentas.persistida(PRESTADOR).getValidacion()).isEqualTo(EstadoValidacion.Activo);
        assertThat(cuentas.persistida(PRESTADOR).getVersion()).isEqualTo(1);
        assertThat(cuentas.revisadoPor(PRESTADOR)).isEqualTo(ADMIN);
        assertThat(outbox.eventos()).singleElement().isEqualTo(new EventoCuentaActualizada(PRESTADOR, Rol.Prestador,
                EstadoAdministrativo.Activo, 1, RNT, T0));
        assertThat(outbox.entregas()).extracting(e -> e.destino)
                .containsExactlyInAnyOrder(Destino.MS_CATALOG, Destino.MS_BOOKING);
        // RabbitMQ (ADR-011): el estado de la cuenta y la decisión para la auditoría de RF13.
        assertThat(outbox.publicables()).containsExactly(new EventoPrestadorValidado(PRESTADOR, true, ADMIN, T0));
        assertThat(outbox.publicaciones()).extracting(p -> p.tipo)
                .containsExactly("CuentaActualizada", "PrestadorValidado");
    }

    /** ADR-011, punto 7: la justificación (texto libre) no viaja en el evento de auditoría. */
    @Test
    void rechazarPublicaLaDecisionSinLaJustificacion() {
        prestador(EstadoValidacion.Pendiente, EstadoCuenta.Activo, 0);

        servicio.validar(new ComandoValidacion(PRESTADOR, "Rechazar", "RNT no vigente", ADMIN));

        assertThat(outbox.publicables()).singleElement().satisfies(evento -> {
            assertThat(evento.datos()).containsEntry("decision", "Rechazado").doesNotContainKey("justificacion");
            assertThat(evento.datos().values()).doesNotContain("RNT no vigente");
        });
    }

    @Test
    void rechazarGuardaLaJustificacionSinEspaciosDeLosExtremos() {
        prestador(EstadoValidacion.Pendiente, EstadoCuenta.Activo, 0);

        PrestadorDetalle detalle = servicio.validar(
                new ComandoValidacion(PRESTADOR, "Rechazar", "  RNT no vigente ", ADMIN));

        assertThat(detalle.estado()).isEqualTo(EstadoAdministrativo.Rechazado);
        assertThat(detalle.motivoRechazo()).isEqualTo("RNT no vigente");
        assertThat(outbox.eventos()).singleElement().extracting(EventoCuentaActualizada::estadoCuenta)
                .isEqualTo(EstadoAdministrativo.Rechazado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rechazarSinJustificacionEsUn400ConElCampoYNoCambiaNada(String justificacion) {
        prestador(EstadoValidacion.Pendiente, EstadoCuenta.Activo, 0);

        assertThatThrownBy(() -> servicio.validar(new ComandoValidacion(PRESTADOR, "Rechazar", justificacion, ADMIN)))
                .isInstanceOfSatisfying(DatosRegistroInvalidosException.class,
                        e -> assertThat(e.getCampos()).containsEntry("justificacion", MotivoInvalido.REQUERIDO));

        assertThat(cuentas.persistida(PRESTADOR).getVersion()).isZero();
        assertThat(outbox.eventos()).isEmpty();
    }

    @Test
    void unaJustificacionDeMasDe500CaracteresEsLongitudMaxima() {
        prestador(EstadoValidacion.Pendiente, EstadoCuenta.Activo, 0);

        assertThatThrownBy(() -> servicio.validar(
                new ComandoValidacion(PRESTADOR, "Rechazar", "a".repeat(501), ADMIN)))
                .isInstanceOfSatisfying(DatosRegistroInvalidosException.class,
                        e -> assertThat(e.getCampos()).containsEntry("justificacion", MotivoInvalido.LONGITUD_MAXIMA));
    }

    @Test
    void unaDecisionDesconocidaEsUnErrorDeFormatoEnElCampoDecision() {
        prestador(EstadoValidacion.Pendiente, EstadoCuenta.Activo, 0);

        assertThatThrownBy(() -> servicio.validar(new ComandoValidacion(PRESTADOR, "Quizás", null, ADMIN)))
                .isInstanceOfSatisfying(DatosRegistroInvalidosException.class,
                        e -> assertThat(e.getCampos()).containsEntry("decision", MotivoInvalido.FORMATO));
        assertThatThrownBy(() -> servicio.validar(new ComandoValidacion(PRESTADOR, null, null, ADMIN)))
                .isInstanceOfSatisfying(DatosRegistroInvalidosException.class,
                        e -> assertThat(e.getCampos()).containsEntry("decision", MotivoInvalido.REQUERIDO));
    }

    @Test
    void aprobarAUnPrestadorQueYaNoEstaPendienteEsEstadoCambiadoConElEstadoActual() {
        prestador(EstadoValidacion.Rechazado, EstadoCuenta.Activo, 1);

        assertThatThrownBy(() -> servicio.validar(new ComandoValidacion(PRESTADOR, "Aprobar", null, ADMIN)))
                .isInstanceOfSatisfying(EstadoCambiadoException.class,
                        e -> assertThat(e.getEstadoActual()).isEqualTo(EstadoAdministrativo.Rechazado));
        assertThat(outbox.eventos()).isEmpty();
    }

    @Test
    void sielEstadoCambioEntreLaLecturaYElUpdateSeResponde409YNoQuedaEvento() {
        // Otro Administrador gana la carrera: después de que este lee la cuenta Pendiente, la aprueba, y el UPDATE
        // condicional ya no encuentra la versión leída.
        AdministrarCuentasService conCarrera = new AdministrarCuentasService(new CuentasAdminEnMemoria() {
            {
                agregar(new CuentaAdministrada(PRESTADOR, Rol.Prestador, EstadoCuenta.Activo,
                        EstadoValidacion.Pendiente, 0, RNT));
            }

            @Override
            public java.util.Optional<CuentaAdministrada> buscarCuenta(UUID idUsuario) {
                java.util.Optional<CuentaAdministrada> leida = super.buscarCuenta(idUsuario);
                // Después de leerla, el otro Administrador la aprueba.
                agregar(new CuentaAdministrada(PRESTADOR, Rol.Prestador, EstadoCuenta.Activo,
                        EstadoValidacion.Activo, 1, RNT));
                return leida;
            }
        }, outbox, new RelojFijo(T0));

        assertThatThrownBy(() -> conCarrera.validar(new ComandoValidacion(PRESTADOR, "Aprobar", null, ADMIN)))
                .isInstanceOfSatisfying(EstadoCambiadoException.class,
                        e -> assertThat(e.getEstadoActual()).isEqualTo(EstadoAdministrativo.Activo));
        assertThat(outbox.eventos()).isEmpty();
    }

    @Test
    void siFallaElEventoLaExcepcionSePropagaParaQueLaTransaccionRevierteElCambio() {
        prestador(EstadoValidacion.Pendiente, EstadoCuenta.Activo, 0);
        outbox.fallarAlRegistrar(new IllegalStateException("outbox caído"));

        // El doble en memoria no revierte; el rollback real lo verifica AdminCuentasApiIntegracionTest. Aquí se
        // comprueba lo que le toca al caso de uso: no tragarse el error.
        assertThatThrownBy(() -> servicio.validar(new ComandoValidacion(PRESTADOR, "Aprobar", null, ADMIN)))
                .isInstanceOf(IllegalStateException.class).hasMessage("outbox caído");
        assertThat(outbox.eventos()).isEmpty();
    }

    @Test
    void unIdDeOtroRolOInexistenteEsCuentaNoEncontrada() {
        turista(EstadoCuenta.Activo, 0);

        assertThatThrownBy(() -> servicio.validar(new ComandoValidacion(TURISTA, "Aprobar", null, ADMIN)))
                .isInstanceOf(CuentaNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.inactivar(UUID.randomUUID())).isInstanceOf(CuentaNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.desactivar(PRESTADOR)).isInstanceOf(CuentaNoEncontradaException.class);
    }

    @Test
    void inactivarUnPrestadorActivoCambiaLaCuentaYEmiteInactivo() {
        prestador(EstadoValidacion.Activo, EstadoCuenta.Activo, 2);

        PrestadorDetalle detalle = servicio.inactivar(PRESTADOR);

        assertThat(detalle.estado()).isEqualTo(EstadoAdministrativo.Inactivo);
        assertThat(cuentas.persistida(PRESTADOR).getCuenta()).isEqualTo(EstadoCuenta.Inactivo);
        assertThat(outbox.eventos()).singleElement().extracting(EventoCuentaActualizada::version).isEqualTo(3L);
    }

    @Test
    void desactivarYReactivarUnTuristaEmitenUnEventoCadaUnoConVersionCreciente() {
        turista(EstadoCuenta.Activo, 0);

        TuristaResumen desactivado = servicio.desactivar(TURISTA);
        TuristaResumen reactivado = servicio.reactivar(TURISTA);

        assertThat(desactivado.estado()).isEqualTo(EstadoAdministrativo.Desactivado);
        assertThat(reactivado.estado()).isEqualTo(EstadoAdministrativo.Activo);
        assertThat(outbox.eventos()).extracting(EventoCuentaActualizada::version).containsExactly(1L, 2L);
        assertThat(outbox.eventos()).extracting(EventoCuentaActualizada::estadoCuenta)
                .containsExactly(EstadoAdministrativo.Desactivado, EstadoAdministrativo.Activo);
    }

    @Test
    void desactivarDosVecesElMismoTuristaEsEstadoCambiado() {
        turista(EstadoCuenta.Activo, 0);
        servicio.desactivar(TURISTA);

        assertThatThrownBy(() -> servicio.desactivar(TURISTA)).isInstanceOf(EstadoCambiadoException.class);
        assertThat(outbox.eventos()).hasSize(1);
    }
}
