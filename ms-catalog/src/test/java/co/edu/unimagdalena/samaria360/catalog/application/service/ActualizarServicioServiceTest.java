package co.edu.unimagdalena.samaria360.catalog.application.service;

import co.edu.unimagdalena.samaria360.catalog.application.fake.OutboxEventosEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.CuentaRepositoryEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.GestionServiciosEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ContenidoInvalidoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.EstadoServicioCambiadoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ServicioModificadoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ServicioNoEncontradoException;
import co.edu.unimagdalena.samaria360.catalog.domain.model.BorradorServicio.DatosContacto;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Borradores;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Contacto;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EstadoPublicacion;
import co.edu.unimagdalena.samaria360.catalog.domain.model.RegistroServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioComercial;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Unidades;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CU «Actualizar tarifas» (RF32) y «Actualizar datos comerciales» (RF43, parte del servicio) con dobles de los puertos:
 * el orden 404, 409 de estado, 400 y escritura condicional, la versión (DEC-186) y la carrera con la decisión del
 * Administrador (RF21).
 */
class ActualizarServicioServiceTest {

    private static final UUID PRESTADOR = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");
    private static final DatosContacto CONTACTO = new DatosContacto("+57 311", "nuevo@servicio.test");

    private final GestionServiciosEnMemoria servicios = new GestionServiciosEnMemoria();
    private final ActualizarServicioService actualizar = new ActualizarServicioService(servicios);
    private final AdministrarServiciosService administrar = new AdministrarServiciosService(servicios,
            new CuentaRepositoryEnMemoria(), new RelojFijo(Instant.parse("2026-10-25T10:00:00Z")), new OutboxEventosEnMemoria());

    private String tour() {
        ServicioComercial servicio = RegistroServicio.registrar(Borradores.tour(), Unidades.nuevoId(),
                Unidades::nuevoId, PRESTADOR, "RNT-1", true, Instant.parse("2026-10-24T15:00:00Z"));
        servicios.agregar(servicio);
        return servicio.getId().orElseThrow();
    }

    @Test
    void cadaEdicionSubeLaVersionYDevuelveElServicioGuardado() {
        String id = tour();

        ServicioComercial conPrecio = actualizar.actualizarPrecio(PRESTADOR, id, new BigDecimal("110000"), null, 0);
        ServicioComercial conDatos = actualizar.actualizarDatosComerciales(PRESTADOR, id, "Tour renovado", CONTACTO, 1);

        assertThat(conPrecio.getVersion()).isEqualTo(1);
        assertThat(conDatos.getVersion()).isEqualTo(2);
        assertThat(conDatos.getPrecioBase()).contains(new BigDecimal("110000"));
        assertThat(conDatos.getNombreComercial()).isEqualTo("Tour renovado");
        assertThat(conDatos.getContacto()).contains(new Contacto("+57 311", "nuevo@servicio.test"));
        assertThat(servicios.guardado(id).getVersion()).isEqualTo(2);
    }

    @Test
    void unServicioPublicadoSeEditaSinNuevaAprobacion() {
        String id = tour();
        administrar.decidir(id, "Aprobar", null, ADMIN);

        ServicioComercial editado = actualizar.actualizarPrecio(PRESTADOR, id, new BigDecimal("99000"), null, 1);

        assertThat(editado.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.Publicado);
        assertThat(editado.getRevision()).isPresent();
        assertThat(editado.getVersion()).isEqualTo(2);
    }

    @Test
    void unServicioAjenoInexistenteOConIdInvalidoEs404() {
        String id = tour();

        for (String otro : new String[]{Unidades.nuevoId(), "no-es-un-id"}) {
            assertThatThrownBy(() -> actualizar.actualizarPrecio(PRESTADOR, otro, BigDecimal.TEN, null, 0))
                    .isInstanceOf(ServicioNoEncontradoException.class);
        }
        assertThatThrownBy(() -> actualizar.actualizarDatosComerciales(UUID.randomUUID(), id, "Ajeno", CONTACTO, 0))
                .isInstanceOf(ServicioNoEncontradoException.class);
        assertThat(servicios.guardado(id).getVersion()).isZero();
    }

    @Test
    void unServicioRechazadoNoSeEditaAunqueLosDatosSeanInvalidos() {
        String id = tour();
        administrar.decidir(id, "Rechazar", "Faltan fotos", ADMIN);

        assertThatThrownBy(() -> actualizar.actualizarPrecio(PRESTADOR, id, null, null, null))
                .isInstanceOfSatisfying(EstadoServicioCambiadoException.class,
                        e -> assertThat(e.getEstadoActual()).isEqualTo(EstadoPublicacion.Rechazado));
    }

    @Test
    void losDatosInvalidosNoEscribenNada() {
        String id = tour();

        assertThatThrownBy(() -> actualizar.actualizarPrecio(PRESTADOR, id, BigDecimal.ZERO, null, 0))
                .isInstanceOf(ContenidoInvalidoException.class);
        assertThat(servicios.guardado(id).getVersion()).isZero();
    }

    @Test
    void unaVersionVieja409ConLaVersionActual() {
        String id = tour();
        actualizar.actualizarPrecio(PRESTADOR, id, new BigDecimal("100000"), null, 0);

        assertThatThrownBy(() -> actualizar.actualizarDatosComerciales(PRESTADOR, id, "Otro", CONTACTO, 0))
                .isInstanceOfSatisfying(ServicioModificadoException.class,
                        e -> assertThat(e.getVersionActual()).isEqualTo(1));
        assertThat(servicios.guardado(id).getNombreComercial()).isEqualTo("Servicio de prueba");
    }

    /** La decisión sube la versión: un precio leído antes de aprobar no pisa el estado (DEC-186). */
    @Test
    void siElAdministradorApruebaEntreLaLecturaYLaEscrituraElPrecioEs409() {
        String id = tour();
        servicios.antesDeEditar(() -> administrar.decidir(id, "Aprobar", null, ADMIN));

        assertThatThrownBy(() -> actualizar.actualizarPrecio(PRESTADOR, id, new BigDecimal("80000"), null, 0))
                .isInstanceOfSatisfying(ServicioModificadoException.class,
                        e -> assertThat(e.getVersionActual()).isEqualTo(1));
        ServicioComercial guardado = servicios.guardado(id);
        assertThat(guardado.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.Publicado);
        assertThat(guardado.getPrecioBase()).contains(new BigDecimal("95000"));
    }

    /** Si lo rechaza en ese intervalo, el 409 es de estado: Rechazado es final. */
    @Test
    void siElAdministradorRechazaEntreLaLecturaYLaEscrituraEs409DeEstado() {
        String id = tour();
        servicios.antesDeEditar(() -> administrar.decidir(id, "Rechazar", "Precio fuera de rango", ADMIN));

        assertThatThrownBy(() -> actualizar.actualizarPrecio(PRESTADOR, id, new BigDecimal("80000"), null, 0))
                .isInstanceOf(EstadoServicioCambiadoException.class);
    }
}
