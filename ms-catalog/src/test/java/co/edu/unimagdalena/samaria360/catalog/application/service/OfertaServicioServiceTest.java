package co.edu.unimagdalena.samaria360.catalog.application.service;

import co.edu.unimagdalena.samaria360.catalog.application.fake.AlmacenImagenesEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.CuentaRepositoryEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.GestionServiciosEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.HistorialModificacionesEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.OfertaServicioEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.OutboxEventosEnMemoria;
import co.edu.unimagdalena.samaria360.catalog.application.fake.RelojFijo;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.OfertaServicioUseCase.CambioRecibido;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ContenidoInvalidoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.EstadoServicioCambiadoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.GaleriaServicioException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ServicioNoEncontradoException;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Borradores;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ImagenSubida;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ModificacionServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.RegistroServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioComercial;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Unidades;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RF41, RF19 (plantilla) y RF48 en ms-catalog con dobles de los puertos (DEC-311, DEC-313): el historial de 30 días,
 * los cambios de ms-booking idempotentes y solo del dueño, y la galería con la compensación de GridFS.
 */
class OfertaServicioServiceTest {

    private static final UUID PRESTADOR = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
    private static final UUID OTRO = UUID.fromString("00000000-0000-4000-8000-0000000000e2");
    private static final UUID ADMIN = UUID.fromString("00000000-0000-4000-8000-0000000000ad");
    private static final Instant AHORA = Instant.parse("2026-11-10T15:00:00Z");
    private static final byte[] PNG = Arrays.copyOf(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 64);

    private final GestionServiciosEnMemoria servicios = new GestionServiciosEnMemoria();
    private final OfertaServicioEnMemoria oferta = new OfertaServicioEnMemoria();
    private final HistorialModificacionesEnMemoria historial = new HistorialModificacionesEnMemoria();
    private final AlmacenImagenesEnMemoria imagenes = new AlmacenImagenesEnMemoria();
    private final RelojFijo reloj = new RelojFijo(AHORA);
    private final OfertaServicioService servicio = new OfertaServicioService(servicios, oferta, historial, imagenes,
            reloj);

    private String registrar(boolean publicar) {
        ServicioComercial nuevo = RegistroServicio.registrar(Borradores.tour(), Unidades.nuevoId(), Unidades::nuevoId,
                PRESTADOR, "RNT-1", true, Instant.parse("2026-10-24T15:00:00Z"));
        servicios.agregar(nuevo);
        String id = nuevo.getId().orElseThrow();
        if (publicar) {
            new AdministrarServiciosService(servicios, new CuentaRepositoryEnMemoria(), reloj,
                    new OutboxEventosEnMemoria()).decidir(id, "Aprobar", null, ADMIN);
        }
        return id;
    }

    private static CambioRecibido cambio(String idEvento, String id, UUID prestador, String tipo, Instant cuando) {
        return new CambioRecibido(idEvento, id, prestador, tipo, "2026-11-20", "08:00", null, "12", "8", null, null,
                cuando);
    }

    // ---- RF19 y RF41: cambios de ms-booking -------------------------------------------------------------------

    @Test
    void unCambioDeCuposQuedaEnElHistorialUnaSolaVez() {
        String id = registrar(false);

        servicio.registrarCambio(cambio("e-1", id, PRESTADOR, "Cupos", AHORA));
        servicio.registrarCambio(cambio("e-1", id, PRESTADOR, "Cupos", AHORA));

        assertThat(historial.todas()).singleElement().satisfies(m -> {
            assertThat(m.tipo()).isEqualTo(ModificacionServicio.Tipo.Cupos);
            assertThat(m.detalle()).isEqualTo("2026-11-20 08:00");
            assertThat(m.valorAnterior()).isEqualTo("12");
            assertThat(m.valorNuevo()).isEqualTo("8");
            assertThat(m.nombreServicio()).isEqualTo(servicios.guardado(id).getNombreComercial());
        });
        assertThat(oferta.plantillas()).isEmpty();
    }

    @Test
    void laPlantillaFijaLaOfertaDelServicio() {
        String id = registrar(false);

        servicio.registrarCambio(new CambioRecibido("e-2", id, PRESTADOR, "Plantilla", null, null, null,
                "Sabado, Domingo · 08:00", "Lunes, Martes · 07:00, 14:00", List.of("Lunes", "Martes"),
                List.of("07:00", "14:00"), AHORA));

        assertThat(oferta.plantillas()).containsExactly(new OfertaServicioEnMemoria.Plantilla(id,
                List.of("Lunes", "Martes"), List.of("07:00", "14:00")));
        assertThat(historial.todas()).singleElement().extracting(ModificacionServicio::detalle).isNull();
    }

    @Test
    void unCambioDeUnServicioAjenoOInexistenteSeIgnora() {
        String id = registrar(false);

        servicio.registrarCambio(cambio("e-3", id, OTRO, "Cupos", AHORA));
        servicio.registrarCambio(cambio("e-4", "65a0000000000000000000ff", PRESTADOR, "Cupos", AHORA));

        assertThat(historial.todas()).isEmpty();
    }

    @Test
    void unTipoDesconocidoUnPrecioOUnIdInvalidoEstanFueraDelContrato() {
        String id = registrar(false);

        assertThatThrownBy(() -> servicio.registrarCambio(cambio("e", id, PRESTADOR, "Otro", AHORA)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.registrarCambio(cambio("e", id, PRESTADOR, "Precio", AHORA)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.registrarCambio(cambio("e", "no-es-id", PRESTADOR, "Cupos", AHORA)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elHistorialMuestraSoloLosUltimos30DiasDelPrestador() {
        String id = registrar(false);
        servicio.registrarCambio(cambio("viejo", id, PRESTADOR, "Cupos", AHORA.minus(Duration.ofDays(31))));
        servicio.registrarCambio(cambio("a", id, PRESTADOR, "Cierre", AHORA.minus(Duration.ofDays(2))));
        servicio.registrarCambio(cambio("b", id, PRESTADOR, "Apertura", AHORA.minus(Duration.ofDays(1))));

        var pagina = servicio.historial(PRESTADOR, null, 0);

        assertThat(pagina.elementos()).extracting(ModificacionServicio::idEvento).containsExactly("b", "a");
        assertThat(pagina.tamano()).isEqualTo(20);
        assertThat(servicio.historial(PRESTADOR, id, 0).totalElementos()).isEqualTo(2);
        assertThat(servicio.historial(OTRO, null, 0).elementos()).isEmpty();
    }

    @Test
    void unFiltroDeServicioOUnaPaginaInvalidosSonContenidoInvalido() {
        assertThatThrownBy(() -> servicio.historial(PRESTADOR, "x", 0)).isInstanceOf(ContenidoInvalidoException.class);
        assertThatThrownBy(() -> servicio.historial(PRESTADOR, null, -1))
                .isInstanceOf(ContenidoInvalidoException.class);
    }

    // ---- RF32 con historial -----------------------------------------------------------------------------------

    @Test
    void elCambioDePrecioQuedaEnElHistorialConElValorAnterior() {
        String id = registrar(false);
        ActualizarServicioService actualizar = new ActualizarServicioService(servicios, historial, reloj);

        actualizar.actualizarPrecio(PRESTADOR, id, new BigDecimal("120000"), null, 0);

        assertThat(historial.todas()).singleElement().satisfies(m -> {
            assertThat(m.tipo()).isEqualTo(ModificacionServicio.Tipo.Precio);
            assertThat(m.valorAnterior()).isEqualTo("95000");
            assertThat(m.valorNuevo()).isEqualTo("120000");
            assertThat(m.fecha()).isEqualTo(AHORA);
        });
    }

    // ---- RF48: galería ----------------------------------------------------------------------------------------

    @Test
    void agregarGuardaLaImagenYLaAgregaALaGaleria() {
        String id = registrar(true);

        servicio.agregarImagen(PRESTADOR, id, new ImagenSubida("playa.png", PNG));

        assertThat(imagenes.ids()).hasSize(1);
        assertThat(oferta.agregadas()).containsExactly("/api/v1/catalogo/imagenes/" + imagenes.ids().iterator().next());
    }

    @Test
    void siLaGaleriaCambioEntreTantoLaImagenSeBorraYEs409() {
        String id = registrar(true);
        oferta.rechazarEscrituras();

        assertThatThrownBy(() -> servicio.agregarImagen(PRESTADOR, id, new ImagenSubida("playa.png", PNG)))
                .isInstanceOfSatisfying(GaleriaServicioException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo(GaleriaServicioException.LIMITE_IMAGENES));
        assertThat(imagenes.ids()).isEmpty();
    }

    @Test
    void unaImagenInvalidaEs400YNoSeGuarda() {
        String id = registrar(true);

        assertThatThrownBy(() -> servicio.agregarImagen(PRESTADOR, id, new ImagenSubida("a.gif", new byte[]{1, 2})))
                .isInstanceOf(ContenidoInvalidoException.class);
        assertThat(imagenes.ids()).isEmpty();
    }

    @Test
    void soloElDuenoYSoloConElServicioPublicado() {
        String pendiente = registrar(false);
        String publicado = registrar(true);
        ImagenSubida png = new ImagenSubida("playa.png", PNG);

        assertThatThrownBy(() -> servicio.agregarImagen(OTRO, publicado, png))
                .isInstanceOf(ServicioNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.agregarImagen(PRESTADOR, "x", png))
                .isInstanceOf(ServicioNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.agregarImagen(PRESTADOR, pendiente, png))
                .isInstanceOf(EstadoServicioCambiadoException.class);
    }

    @Test
    void quitarUnaRutaQueNoEstaEnLaGaleriaEs400() {
        String id = registrar(true);

        assertThatThrownBy(() -> servicio.quitarImagen(PRESTADOR, id, "/api/v1/catalogo/imagenes/otra"))
                .isInstanceOf(ContenidoInvalidoException.class);
        assertThat(oferta.quitadas()).isEmpty();
    }
}
