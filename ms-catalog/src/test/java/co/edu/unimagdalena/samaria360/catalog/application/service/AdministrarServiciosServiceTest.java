package co.edu.unimagdalena.samaria360.catalog.application.service;

import co.edu.unimagdalena.samaria360.catalog.application.fake.OutboxEventosEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.CuentaRepositoryEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.GestionServiciosEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.AdministrarServiciosUseCase.ServicioEnRevision;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ContenidoInvalidoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.EstadoServicioCambiadoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ServicioNoEncontradoException;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Borradores;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Cuenta;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EstadoCuenta;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EstadoPublicacion;
import co.edu.unimagdalena.samaria360.catalog.domain.model.MotivoInvalido;
import co.edu.unimagdalena.samaria360.catalog.domain.model.PaginaResultados;
import co.edu.unimagdalena.samaria360.catalog.domain.model.RegistroServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Revision;
import co.edu.unimagdalena.samaria360.catalog.domain.model.RolCuenta;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioComercial;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Unidades;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * CU «Aprobar o rechazar servicio» con dobles de los puertos (RF21; DEC-128, DEC-144, DEC-145), con «Consultar listado
 * pendiente» y «Justificar rechazo de servicio».
 */
class AdministrarServiciosServiceTest {

    private static final UUID PRESTADOR = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");
    private static final Instant T0 = Instant.parse("2026-10-20T10:00:00Z");

    private final GestionServiciosEnMemoria servicios = new GestionServiciosEnMemoria();
    private final CuentaRepositoryEnMemoria cuentas = new CuentaRepositoryEnMemoria();
    private final RelojFijo reloj = new RelojFijo(Instant.parse("2026-10-24T15:00:00.987654Z"));
    private final OutboxEventosEnMemoria outbox = new OutboxEventosEnMemoria();
    private final AdministrarServiciosService administrar =
            new AdministrarServiciosService(servicios, cuentas, reloj, outbox);

    private String pendiente(Instant fecha) {
        ServicioComercial servicio = RegistroServicio.registrar(Borradores.tour(), Unidades.nuevoId(),
                Unidades::nuevoId, PRESTADOR, "RNT-1", true, fecha);
        servicios.agregar(servicio);
        return servicio.getId().orElseThrow();
    }

    @Test
    void laColaDePendientesVaDelMasAntiguoAlMasRecienteEnPaginasDeDiez() {
        String reciente = pendiente(T0.plus(Duration.ofDays(2)));
        String antiguo = pendiente(T0);
        String medio = pendiente(T0.plus(Duration.ofDays(1)));
        for (int i = 0; i < 9; i++) {
            pendiente(T0.plus(Duration.ofDays(10 + i)));
        }

        PaginaResultados<ServicioComercial> primera = administrar.listar(null, 0);

        assertThat(primera.elementos()).hasSize(10);
        assertThat(primera.totalElementos()).isEqualTo(12);
        assertThat(primera.elementos().subList(0, 3)).extracting(s -> s.getId().orElseThrow())
                .containsExactly(antiguo, medio, reciente);
        assertThat(administrar.listar("", 1).elementos()).hasSize(2);
    }

    @Test
    void elListadoFiltraPorEstadoYRechazaUnEstadoOPaginaInvalidos() {
        String id = pendiente(T0);
        administrar.decidir(id, "Aprobar", null, ADMIN);

        assertThat(administrar.listar("Publicado", 0).elementos()).extracting(s -> s.getId().orElseThrow())
                .containsExactly(id);
        assertThat(administrar.listar("Pendiente", 0).elementos()).isEmpty();
        assertThatThrownBy(() -> administrar.listar("Borrador", 0))
                .isInstanceOfSatisfying(ContenidoInvalidoException.class,
                        e -> assertThat(e.getCampos()).containsOnly(entry("estado", MotivoInvalido.NO_PERMITIDO)));
        assertThatThrownBy(() -> administrar.listar(null, -1))
                .isInstanceOfSatisfying(ContenidoInvalidoException.class,
                        e -> assertThat(e.getCampos()).containsOnly(entry("pagina", MotivoInvalido.FORMATO)));
    }

    @Test
    void elDetalleTraeElServicioEnCualquierEstadoYElEstadoDeLaCuentaDelPrestador() {
        String id = pendiente(T0);
        cuentas.agregar(new Cuenta(PRESTADOR, RolCuenta.Prestador, EstadoCuenta.Activo, 2, "RNT-1"));

        ServicioEnRevision detalle = administrar.detalle(id);

        assertThat(detalle.servicio().getEstadoPublicacion()).isEqualTo(EstadoPublicacion.Pendiente);
        assertThat(detalle.estadoCuentaPrestador()).contains(EstadoCuenta.Activo);
        assertThatThrownBy(() -> administrar.detalle(Unidades.nuevoId()))
                .isInstanceOf(ServicioNoEncontradoException.class);
        assertThatThrownBy(() -> administrar.detalle("no-es-un-id")).isInstanceOf(ServicioNoEncontradoException.class);
    }

    @Test
    void aprobarLoPublicaConQuienYCuandoSinMotivo() {
        String id = pendiente(T0);

        ServicioComercial aprobado = administrar.decidir(id, "Aprobar", "se ignora", ADMIN).servicio();

        assertThat(aprobado.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.Publicado);
        assertThat(aprobado.getRevision()).contains(new Revision(ADMIN, Instant.parse("2026-10-24T15:00:00.987Z"),
                null));
        assertThat(aprobado.esVisibleEnBusquedaPublica()).isTrue();
        // Auditoría de RF13 (ADR-011, DEC-293): la decisión, sin texto libre.
        assertThat(outbox.eventos()).singleElement().satisfies(evento -> {
            assertThat(evento.tipo()).isEqualTo("ServicioDecidido");
            assertThat(evento.clave()).isEqualTo(id);
            assertThat(evento.datos()).containsEntry("decision", "Publicado")
                    .containsEntry("idAdministrador", ADMIN.toString())
                    .containsEntry("idPrestador", aprobado.getPrestadorId().toString());
        });
    }

    @Test
    void rechazarPublicaLaDecisionSinLaJustificacion() {
        String id = pendiente(T0);

        administrar.decidir(id, "Rechazar", "Faltan fotos", ADMIN);

        assertThat(outbox.eventos()).singleElement().satisfies(evento -> {
            assertThat(evento.datos()).containsEntry("decision", "Rechazado");
            assertThat(evento.datos().values()).doesNotContain("Faltan fotos");
        });
    }

    @Test
    void rechazarGuardaLaJustificacionSinLosEspaciosDeLosExtremos() {
        String id = pendiente(T0);

        ServicioComercial rechazado = administrar.decidir(id, "Rechazar", "  Faltan fotos  ", ADMIN).servicio();

        assertThat(rechazado.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.Rechazado);
        assertThat(rechazado.getRevision()).hasValueSatisfying(r -> {
            assertThat(r.motivoRechazo()).isEqualTo("Faltan fotos");
            assertThat(r.revisadoPor()).isEqualTo(ADMIN);
        });
    }

    @Test
    void rechazarSinJustificacionOConMasDe500CaracteresEs400YNoCambiaNada() {
        String id = pendiente(T0);

        for (String justificacion : new String[]{null, "   ", "x".repeat(501)}) {
            assertThatThrownBy(() -> administrar.decidir(id, "Rechazar", justificacion, ADMIN))
                    .isInstanceOf(ContenidoInvalidoException.class);
        }
        assertThat(administrar.decidir(id, "Rechazar", "x".repeat(500), ADMIN).servicio().getEstadoPublicacion())
                .isEqualTo(EstadoPublicacion.Rechazado);
    }

    @Test
    void unaDecisionAusenteOFueraDelVocabularioEs400() {
        String id = pendiente(T0);

        assertThatThrownBy(() -> administrar.decidir(id, null, null, ADMIN))
                .isInstanceOfSatisfying(ContenidoInvalidoException.class,
                        e -> assertThat(e.getCampos()).containsOnly(entry("decision", MotivoInvalido.REQUERIDO)));
        assertThatThrownBy(() -> administrar.decidir(id, "Publicar", null, ADMIN))
                .isInstanceOfSatisfying(ContenidoInvalidoException.class,
                        e -> assertThat(e.getCampos()).containsOnly(entry("decision", MotivoInvalido.NO_PERMITIDO)));
        assertThat(servicios.guardado(id).getEstadoPublicacion()).isEqualTo(EstadoPublicacion.Pendiente);
    }

    @Test
    void unaSegundaDecisionEs409ConElEstadoActual() {
        String id = pendiente(T0);
        administrar.decidir(id, "Rechazar", "RNT no corresponde", ADMIN);

        assertThatThrownBy(() -> administrar.decidir(id, "Aprobar", null, ADMIN))
                .isInstanceOfSatisfying(EstadoServicioCambiadoException.class,
                        e -> assertThat(e.getEstadoActual()).isEqualTo(EstadoPublicacion.Rechazado));
        assertThat(servicios.guardado(id).getRevision()).hasValueSatisfying(
                r -> assertThat(r.motivoRechazo()).isEqualTo("RNT no corresponde"));
    }

    @Test
    void unIdInexistenteOInvalidoEs404() {
        assertThatThrownBy(() -> administrar.decidir(Unidades.nuevoId(), "Aprobar", null, ADMIN))
                .isInstanceOf(ServicioNoEncontradoException.class);
        assertThatThrownBy(() -> administrar.decidir("no-es-un-id", "Aprobar", null, ADMIN))
                .isInstanceOf(ServicioNoEncontradoException.class);
    }

    /** DEC-145: aprobar el servicio de un prestador ya inactivo lo publica, pero sigue oculto (RF22). */
    @Test
    void aprobarElServicioDeUnPrestadorInactivoLoDejaPublicadoPeroOculto() {
        ServicioComercial servicio = RegistroServicio.registrar(Borradores.tour(), Unidades.nuevoId(),
                Unidades::nuevoId, PRESTADOR, "RNT-1", false, T0);
        servicios.agregar(servicio);

        ServicioComercial aprobado = administrar.decidir(servicio.getId().orElseThrow(), "Aprobar", null, ADMIN)
                .servicio();

        assertThat(aprobado.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.Publicado);
        assertThat(aprobado.esVisibleEnBusquedaPublica()).isFalse();
    }
}
