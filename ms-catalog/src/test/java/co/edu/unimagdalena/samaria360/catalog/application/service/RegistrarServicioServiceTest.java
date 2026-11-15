package co.edu.unimagdalena.samaria360.catalog.application.service;

import co.edu.unimagdalena.samaria360.catalog.application.fake.CuentaRepositoryEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.GestionServiciosEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ContenidoInvalidoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.PrestadorNoHabilitadoException;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Borradores;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Cuenta;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EstadoCuenta;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EstadoPublicacion;
import co.edu.unimagdalena.samaria360.catalog.domain.model.RolCuenta;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioComercial;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CU «Registrar servicio» con dobles de los puertos (RF09; DEC-124, DEC-125, DEC-126, DEC-142). */
class RegistrarServicioServiceTest {

    private static final UUID PRESTADOR = UUID.fromString("00000000-0000-4000-8000-0000000000d1");
    private static final Instant AHORA = Instant.parse("2026-10-24T15:00:00.123456Z");

    private final GestionServiciosEnMemoria servicios = new GestionServiciosEnMemoria();
    private final CuentaRepositoryEnMemoria cuentas = new CuentaRepositoryEnMemoria();
    private final RegistrarServicioService registrar =
            new RegistrarServicioService(servicios, cuentas, new RelojFijo(AHORA));

    @Test
    void unPrestadorActivoConRntRegistraUnServicioPendienteConLosDatosDeLaProyeccion() {
        cuentas.agregar(new Cuenta(PRESTADOR, RolCuenta.Prestador, EstadoCuenta.Activo, 3, "RNT-54321"));

        ServicioComercial servicio = registrar.registrar(PRESTADOR, Borradores.tour());

        assertThat(servicio.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.Pendiente);
        assertThat(servicio.getPrestadorId()).isEqualTo(PRESTADOR);
        assertThat(servicio.getRnt()).contains("RNT-54321");
        assertThat(servicio.isPrestadorActivo()).isTrue();
        assertThat(servicio.getFechaRegistro()).contains(Instant.parse("2026-10-24T15:00:00.123Z"));
        assertThat(servicios.guardado(servicio.getId().orElseThrow()).isPrestadorActivo()).isTrue();
    }

    @Test
    void sinFilaEnLaProyeccionEs403YNoSeGuardaNada() {
        assertThatThrownBy(() -> registrar.registrar(PRESTADOR, Borradores.tour()))
                .isInstanceOf(PrestadorNoHabilitadoException.class);
        assertThat(servicios.todos()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCuenta.class, names = {"Pendiente", "Rechazado", "Inactivo"})
    void unPrestadorQueNoEstaActivoNoRegistra(EstadoCuenta estado) {
        cuentas.agregar(new Cuenta(PRESTADOR, RolCuenta.Prestador, estado, 3, "RNT-54321"));

        assertThatThrownBy(() -> registrar.registrar(PRESTADOR, Borradores.tour()))
                .isInstanceOf(PrestadorNoHabilitadoException.class);
        assertThat(servicios.todos()).isEmpty();
    }

    /** Una proyección anterior a V17 de ms-auth no tiene RNT: el servicio no podría guardar su copia (DEC-126). */
    @Test
    void unaProyeccionSinRntNoRegistra() {
        cuentas.agregar(new Cuenta(PRESTADOR, RolCuenta.Prestador, EstadoCuenta.Activo, 1));

        assertThatThrownBy(() -> registrar.registrar(PRESTADOR, Borradores.tour()))
                .isInstanceOf(PrestadorNoHabilitadoException.class);
    }

    /** Primero la cuenta y después los datos: un prestador no habilitado no recibe el 400. */
    @Test
    void laCuentaSeRevisaAntesQueLosDatos() {
        assertThatThrownBy(() -> registrar.registrar(PRESTADOR, Borradores.alojamiento(java.util.List.of())))
                .isInstanceOf(PrestadorNoHabilitadoException.class);
        cuentas.agregar(new Cuenta(PRESTADOR, RolCuenta.Prestador, EstadoCuenta.Activo, 3, "RNT-54321"));
        assertThatThrownBy(() -> registrar.registrar(PRESTADOR, Borradores.alojamiento(java.util.List.of())))
                .isInstanceOf(ContenidoInvalidoException.class);
        assertThat(servicios.todos()).isEmpty();
    }

    /**
     * DEC-142 (a), ajuste 5: la cuenta pasa a Inactivo entre la lectura y el {@code insert}. La inactivación ya corrió su
     * {@code updateMulti} (sin este servicio) y guardó la proyección; el registro la relee después de insertar y oculta
     * el servicio.
     */
    @Test
    void siLaCuentaSeInactivaEntreLaLecturaYElInsertElServicioQuedaOculto() {
        cuentas.agregar(new Cuenta(PRESTADOR, RolCuenta.Prestador, EstadoCuenta.Activo, 3, "RNT-54321"));
        GestionServiciosEnMemoria conCarrera = new GestionServiciosEnMemoria() {
            @Override
            public void insertar(ServicioComercial servicio) {
                // La inactivación llega justo antes del insert: updateMulti (todavía sin este servicio) y proyección.
                visibilidad().fijarPrestadorActivo(PRESTADOR.toString(), false);
                cuentas.agregar(new Cuenta(PRESTADOR, RolCuenta.Prestador, EstadoCuenta.Inactivo, 4, "RNT-54321"));
                super.insertar(servicio);
            }
        };
        RegistrarServicioService registro = new RegistrarServicioService(conCarrera, cuentas, new RelojFijo(AHORA));

        ServicioComercial servicio = registro.registrar(PRESTADOR, Borradores.tour());

        assertThat(servicio.isPrestadorActivo()).isFalse();
        assertThat(conCarrera.guardado(servicio.getId().orElseThrow()).isPrestadorActivo()).isFalse();
        assertThat(conCarrera.guardado(servicio.getId().orElseThrow()).esVisibleEnBusquedaPublica()).isFalse();
    }

    private static final byte[] JPEG = java.util.Arrays.copyOf(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 32);

    /** DEC-318: con fotos, se guardan en el almacén y el servicio lleva sus rutas. */
    @Test
    void conFotosGuardaLasImagenesYElServicioLasReferencia() {
        cuentas.agregar(new Cuenta(PRESTADOR, RolCuenta.Prestador, EstadoCuenta.Activo, 3, "RNT-54321"));
        co.edu.unimagdalena.samaria360.catalog.application.fake.AlmacenImagenesEnMemoria almacen =
                new co.edu.unimagdalena.samaria360.catalog.application.fake.AlmacenImagenesEnMemoria();
        RegistrarServicioService conFotos = new RegistrarServicioService(servicios, cuentas, new RelojFijo(AHORA), almacen);

        ServicioComercial servicio = conFotos.registrar(PRESTADOR, Borradores.tour(), java.util.List.of(
                new co.edu.unimagdalena.samaria360.catalog.domain.model.ImagenSubida("a.jpg", JPEG)));

        assertThat(almacen.ids()).hasSize(1);
        assertThat(servicio.getImagenes()).containsExactly("/api/v1/catalogo/imagenes/" + almacen.ids().iterator().next());
        assertThat(servicios.guardado(servicio.getId().orElseThrow()).getImagenes()).hasSize(1);
    }

    /** Si una imagen no se guarda, se borran las anteriores y el servicio no se inserta (compensación). */
    @Test
    void siFallaUnaImagenSeBorranLasGuardadasYNoQuedaElServicio() {
        cuentas.agregar(new Cuenta(PRESTADOR, RolCuenta.Prestador, EstadoCuenta.Activo, 3, "RNT-54321"));
        co.edu.unimagdalena.samaria360.catalog.application.fake.AlmacenImagenesEnMemoria almacen =
                new co.edu.unimagdalena.samaria360.catalog.application.fake.AlmacenImagenesEnMemoria();
        almacen.fallarAlGuardarLaNumero(2, new IllegalStateException("GridFS caído"));
        RegistrarServicioService conFotos = new RegistrarServicioService(servicios, cuentas, new RelojFijo(AHORA), almacen);
        co.edu.unimagdalena.samaria360.catalog.domain.model.ImagenSubida imagen =
                new co.edu.unimagdalena.samaria360.catalog.domain.model.ImagenSubida("a.jpg", JPEG);

        assertThatThrownBy(() -> conFotos.registrar(PRESTADOR, Borradores.tour(), java.util.List.of(imagen, imagen)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(almacen.ids()).isEmpty();
        assertThat(servicios.todos()).isEmpty();
    }
}
