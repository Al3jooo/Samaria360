package co.edu.unimagdalena.samaria360.catalog.application.service;

import co.edu.unimagdalena.samaria360.catalog.application.fake.OutboxEventosEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.CuentaRepositoryEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.GestionServiciosEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ContenidoInvalidoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ServicioNoEncontradoException;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Borradores;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EstadoPublicacion;
import co.edu.unimagdalena.samaria360.catalog.domain.model.PaginaResultados;
import co.edu.unimagdalena.samaria360.catalog.domain.model.RegistroServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioComercial;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Unidades;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Flujo «Consultar mis servicios» del CU «Registrar servicio» con dobles de los puertos (DEC-129, DEC-143). */
class ConsultarMisServiciosServiceTest {

    private static final UUID PRESTADOR = UUID.fromString("00000000-0000-4000-8000-0000000000f1");
    private static final UUID OTRO = UUID.fromString("00000000-0000-4000-8000-0000000000f2");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");
    private static final Instant T0 = Instant.parse("2026-10-20T10:00:00Z");

    private final GestionServiciosEnMemoria servicios = new GestionServiciosEnMemoria();
    private final ConsultarMisServiciosService consultar = new ConsultarMisServiciosService(servicios);
    private final AdministrarServiciosService administrar =
            new AdministrarServiciosService(servicios, new CuentaRepositoryEnMemoria(), new RelojFijo(T0),
                    new OutboxEventosEnMemoria());

    private String registrar(UUID prestador, Instant fecha) {
        ServicioComercial servicio = RegistroServicio.registrar(Borradores.gastronomia(), Unidades.nuevoId(),
                Unidades::nuevoId, prestador, "RNT-1", true, fecha);
        servicios.agregar(servicio);
        return servicio.getId().orElseThrow();
    }

    @Test
    void soloListaLosDelPrestadorDelMasRecienteAlMasAntiguo() {
        String antiguo = registrar(PRESTADOR, T0);
        String reciente = registrar(PRESTADOR, T0.plus(Duration.ofHours(1)));
        registrar(OTRO, T0.plus(Duration.ofHours(2)));

        PaginaResultados<ServicioComercial> pagina = consultar.listar(PRESTADOR, null, 0);

        assertThat(pagina.elementos()).extracting(s -> s.getId().orElseThrow()).containsExactly(reciente, antiguo);
        assertThat(pagina.totalElementos()).isEqualTo(2);
    }

    @Test
    void filtraPorEstadoPaginaDeADiezYMuestraElMotivoDelRechazo() {
        String rechazado = registrar(PRESTADOR, T0);
        administrar.decidir(rechazado, "Rechazar", "La dirección no coincide con el pin", ADMIN);
        for (int i = 1; i <= 11; i++) {
            registrar(PRESTADOR, T0.plus(Duration.ofMinutes(i)));
        }

        assertThat(consultar.listar(PRESTADOR, "Pendiente", 0).elementos()).hasSize(10);
        assertThat(consultar.listar(PRESTADOR, "Pendiente", 1).elementos()).hasSize(1);
        assertThat(consultar.listar(PRESTADOR, "Rechazado", 0).elementos()).singleElement().satisfies(s -> {
            assertThat(s.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.Rechazado);
            assertThat(s.getRevision().orElseThrow().motivoRechazo()).isEqualTo("La dirección no coincide con el pin");
        });
        assertThat(consultar.listar(PRESTADOR, " ", 0).totalElementos()).isEqualTo(12);
        assertThatThrownBy(() -> consultar.listar(PRESTADOR, "Todos", 0))
                .isInstanceOf(ContenidoInvalidoException.class);
        assertThatThrownBy(() -> consultar.listar(PRESTADOR, null, -1)).isInstanceOf(ContenidoInvalidoException.class);
    }

    @Test
    void elDetalleDeUnServicioAjenoInexistenteOInvalidoEs404() {
        String propio = registrar(PRESTADOR, T0);
        String ajeno = registrar(OTRO, T0);

        assertThat(consultar.detalle(PRESTADOR, propio).getId()).contains(propio);
        assertThatThrownBy(() -> consultar.detalle(PRESTADOR, ajeno)).isInstanceOf(ServicioNoEncontradoException.class);
        assertThatThrownBy(() -> consultar.detalle(PRESTADOR, Unidades.nuevoId()))
                .isInstanceOf(ServicioNoEncontradoException.class);
        assertThatThrownBy(() -> consultar.detalle(PRESTADOR, "no-es-un-id"))
                .isInstanceOf(ServicioNoEncontradoException.class);
    }
}
