package co.edu.unimagdalena.samaria360.catalog.domain.model;

import co.edu.unimagdalena.samaria360.catalog.domain.exception.ContenidoInvalidoException;
import co.edu.unimagdalena.samaria360.catalog.domain.model.BorradorServicio.DatosContacto;
import co.edu.unimagdalena.samaria360.catalog.domain.model.BorradorServicio.DatosCoordenadas;
import co.edu.unimagdalena.samaria360.catalog.domain.model.BorradorServicio.DatosDetalle;
import co.edu.unimagdalena.samaria360.catalog.domain.model.BorradorServicio.DatosOferta;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/** CU «Registrar servicio» en el dominio: el servicio Pendiente y la validación por campo (RF09, RF10, RF37; DEC-138). */
class RegistroServicioTest {

    private static final UUID PRESTADOR = UUID.fromString("00000000-0000-4000-8000-0000000000c1");
    private static final Instant AHORA = Instant.parse("2026-10-24T15:00:00Z");

    private static ServicioComercial registrar(BorradorServicio borrador) {
        return RegistroServicio.registrar(borrador, Unidades.nuevoId(), Unidades::nuevoId, PRESTADOR, "RNT-777", true,
                AHORA);
    }

    private static Map<String, MotivoInvalido> errores(BorradorServicio borrador) {
        try {
            registrar(borrador);
        } catch (ContenidoInvalidoException e) {
            return e.getCampos();
        }
        throw new AssertionError("Se esperaba ContenidoInvalidoException");
    }

    @Test
    void unServicioValidoQuedaPendienteConLosDatosDelServidor() {
        ServicioComercial servicio = registrar(Borradores.tour());

        assertThat(servicio.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.Pendiente);
        assertThat(servicio.getPrestadorId()).isEqualTo(PRESTADOR);
        assertThat(servicio.getRnt()).contains("RNT-777");
        assertThat(servicio.isPrestadorActivo()).isTrue();
        assertThat(servicio.getFechaRegistro()).contains(AHORA);
        assertThat(servicio.getRevision()).isEmpty();
        assertThat(servicio.getNombreComercial()).isEqualTo("Servicio de prueba");
        assertThat(servicio.getDireccion()).contains("Calle 1 # 2-3");
        assertThat(servicio.getCondicionesAccesibilidad())
                .containsExactlyInAnyOrder(CondicionAccesibilidad.Visual, CondicionAccesibilidad.MovilidadReducida);
        assertThat(servicio.getHorarioAtencion()).contains("Lunes a domingo, 8:00 a 18:00");
        assertThat(servicio.getPrecioBase()).contains(new BigDecimal("95000"));
        assertThat(servicio.getOferta()).hasValueSatisfying(oferta -> {
            assertThat(oferta.diasOperacion()).containsExactly(DiaSemana.Sabado, DiaSemana.Domingo);
            assertThat(oferta.salidas()).containsExactly(LocalTime.of(8, 0), LocalTime.of(14, 0));
        });
    }

    @Test
    void cadaCategoriaSeRegistraConSuDetalleYSoloTourYAlojamientoTienenOferta() {
        assertThat(registrar(Borradores.alojamiento()).getDetalle()).isInstanceOf(DetalleAlojamiento.class);
        assertThat(registrar(Borradores.alojamiento()).getOferta()).hasValueSatisfying(
                oferta -> assertThat(oferta.salidas()).isEmpty());
        assertThat(registrar(Borradores.tour()).getDetalle()).isInstanceOf(DetalleTour.class);
        ServicioComercial gastronomia = registrar(Borradores.gastronomia());
        assertThat(gastronomia.getDetalle()).isEqualTo(
                new DetalleGastronomia("Caribeña", 60, true, "$30.000 - $60.000"));
        assertThat(gastronomia.getOferta()).isEmpty();
        ServicioComercial alquiler = registrar(Borradores.alquiler());
        assertThat(alquiler.getDetalle()).isEqualTo(new DetalleAlquilerEquipo("Kayak doble", 8, "Depósito de $50.000"));
        assertThat(alquiler.getOferta()).isEmpty();
    }

    @Test
    void unAlojamientoConUnaOVariasUnidadesDerivaElPrecioDesdeYGeneraLosIds() {
        ServicioComercial una = registrar(Borradores.alojamiento());
        ServicioComercial varias = registrar(Borradores.alojamiento(List.of(
                Borradores.unidad("Suite", 2, 4, "320000"),
                Borradores.unidad("Doble", 8, 2, "150000"),
                Borradores.unidad("Familiar", 3, 5, "260000"))));

        assertThat(una.getPrecioBase()).contains(new BigDecimal("180000"));
        assertThat(varias.getPrecioBase()).contains(new BigDecimal("150000"));
        List<UnidadAlojamiento> unidades = ((DetalleAlojamiento) varias.getDetalle()).unidades();
        assertThat(unidades).extracting(UnidadAlojamiento::nombre).containsExactly("Suite", "Doble", "Familiar");
        assertThat(unidades).extracting(UnidadAlojamiento::idUnidad).doesNotHaveDuplicates()
                .allMatch(IdentificadorObjeto::esValido);
    }

    /** DEC-127: en Alojamiento el precio «desde» es derivado; el que llegue se ignora. */
    @Test
    void elPrecioBaseDeUnAlojamientoSeIgnora() {
        BorradorServicio conPrecio = cambiar(Borradores.alojamiento(), b -> new BorradorServicio(b.nombreComercial(),
                b.categoria(), b.descripcion(), b.direccion(), b.coordenadas(), b.contacto(),
                b.condicionesAccesibilidad(), b.horarioAtencion(), new BigDecimal("1"), b.detalleCategoria(),
                b.oferta()));

        assertThat(registrar(conPrecio).getPrecioBase()).contains(new BigDecimal("180000"));
    }

    @Test
    void losDatosGeneralesSeValidanCampoPorCampo() {
        BorradorServicio invalido = new BorradorServicio("  ", null, "x".repeat(2001), null,
                new DatosCoordenadas(-181.0, 91.0), new DatosContacto("1".repeat(21), "sin-arroba"),
                List.of("Visual", "Olfativa", "Visual"), "h".repeat(501), null, null, null);

        assertThat(errores(invalido)).containsOnly(
                entry("nombreComercial", MotivoInvalido.REQUERIDO),
                entry("categoria", MotivoInvalido.REQUERIDO),
                entry("descripcion", MotivoInvalido.LONGITUD_MAXIMA),
                entry("direccion", MotivoInvalido.REQUERIDO),
                entry("coordenadas.longitud", MotivoInvalido.VALOR_MINIMO),
                entry("coordenadas.latitud", MotivoInvalido.VALOR_MAXIMO),
                entry("contacto.telefono", MotivoInvalido.LONGITUD_MAXIMA),
                entry("contacto.correo", MotivoInvalido.FORMATO),
                entry("condicionesAccesibilidad[1]", MotivoInvalido.NO_PERMITIDO),
                entry("condicionesAccesibilidad[2]", MotivoInvalido.DUPLICADO),
                entry("horarioAtencion", MotivoInvalido.LONGITUD_MAXIMA));
    }

    @Test
    void sinCoordenadasNiContactoNiDetalleNiOfertaSonRequeridos() {
        BorradorServicio b = Borradores.tour();
        BorradorServicio vacio = new BorradorServicio(b.nombreComercial(), "Tour", b.descripcion(), b.direccion(),
                null, null, null, null, null, null, null);

        assertThat(errores(vacio)).containsOnly(
                entry("coordenadas", MotivoInvalido.REQUERIDO),
                entry("contacto", MotivoInvalido.REQUERIDO),
                entry("precioBase", MotivoInvalido.REQUERIDO),
                entry("detalleCategoria", MotivoInvalido.REQUERIDO),
                entry("oferta", MotivoInvalido.REQUERIDO));
    }

    @Test
    void unaCategoriaFueraDelVocabularioNoValidaElDetalle() {
        BorradorServicio b = Borradores.tour();

        assertThat(errores(new BorradorServicio(b.nombreComercial(), "Spa", b.descripcion(), b.direccion(),
                b.coordenadas(), b.contacto(), null, null, null, null, null)))
                .containsOnly(entry("categoria", MotivoInvalido.NO_PERMITIDO));
    }

    @Test
    void lasUnidadesSeValidanConSuIndice() {
        List<BorradorServicio.DatosUnidad> unidades = new ArrayList<>();
        unidades.add(Borradores.unidad("Doble", 2, 2, "150000"));
        unidades.add(new BorradorServicio.DatosUnidad(" ", "", 0, 51, new BigDecimal("12.345")));
        unidades.add(null);

        assertThat(errores(Borradores.alojamiento(unidades))).containsOnly(
                entry("detalleCategoria.unidades[1].nombre", MotivoInvalido.REQUERIDO),
                entry("detalleCategoria.unidades[1].tipoHabitacion", MotivoInvalido.REQUERIDO),
                entry("detalleCategoria.unidades[1].numHabitaciones", MotivoInvalido.VALOR_MINIMO),
                entry("detalleCategoria.unidades[1].capacidadMaxHuespedes", MotivoInvalido.VALOR_MAXIMO),
                entry("detalleCategoria.unidades[1].precioNoche", MotivoInvalido.FORMATO),
                entry("detalleCategoria.unidades[2]", MotivoInvalido.REQUERIDO));
    }

    @Test
    void unAlojamientoTieneDeUnaAVeinteUnidades() {
        assertThat(errores(Borradores.alojamiento(List.of())))
                .containsOnly(entry("detalleCategoria.unidades", MotivoInvalido.CANTIDAD_MINIMA));
        assertThat(errores(Borradores.alojamiento(Collections.nCopies(21, Borradores.unidad("D", 1, 1, "1")))))
                .containsOnly(entry("detalleCategoria.unidades", MotivoInvalido.CANTIDAD_MAXIMA));
        assertThat(registrar(Borradores.alojamiento(Collections.nCopies(20, Borradores.unidad("D", 1, 1, "1"))))
                .getDetalle()).satisfies(d -> assertThat(((DetalleAlojamiento) d).unidades()).hasSize(20));
    }

    @Test
    void losPreciosSonPositivosConDosDecimalesYCabenEnLaReserva() {
        assertThat(errores(conPrecio(Borradores.tour(), "0"))).containsOnly(entry("precioBase", MotivoInvalido.VALOR_MINIMO));
        assertThat(errores(conPrecio(Borradores.tour(), "100000000")))
                .containsOnly(entry("precioBase", MotivoInvalido.VALOR_MAXIMO));
        assertThat(errores(conPrecio(Borradores.tour(), "1.001"))).containsOnly(entry("precioBase", MotivoInvalido.FORMATO));
        assertThat(registrar(conPrecio(Borradores.tour(), "99999999.99")).getPrecioBase())
                .contains(new BigDecimal("99999999.99"));
    }

    @Test
    void elDetalleDeCadaCategoriaSeValida() {
        DatosDetalle tour = new DatosDetalle(null, null, null, null, null, null, "", new BigDecimal("0"), null, 0,
                null, null, null);
        DatosDetalle gastronomia = new DatosDetalle(null, null, null, 10_001, null, "x".repeat(51), null, null, null,
                null, null, null, null);
        DatosDetalle alquiler = new DatosDetalle(null, null, null, null, null, null, null, null, null, null, "Kayak",
                0, null);

        assertThat(errores(conDetalle(Borradores.tour(), tour))).containsOnly(
                entry("detalleCategoria.puntoEncuentro", MotivoInvalido.REQUERIDO),
                entry("detalleCategoria.duracionHoras", MotivoInvalido.VALOR_MINIMO),
                entry("detalleCategoria.aforoMaxGrupo", MotivoInvalido.VALOR_MINIMO));
        assertThat(errores(conDetalle(Borradores.gastronomia(), gastronomia))).containsOnly(
                entry("detalleCategoria.tipoCocina", MotivoInvalido.REQUERIDO),
                entry("detalleCategoria.aforoMaxTurno", MotivoInvalido.VALOR_MAXIMO),
                entry("detalleCategoria.rangoPrecioPlato", MotivoInvalido.LONGITUD_MAXIMA));
        assertThat(errores(conDetalle(Borradores.alquiler(), alquiler)))
                .containsOnly(entry("detalleCategoria.unidadesDisponibles", MotivoInvalido.VALOR_MINIMO));
    }

    @Test
    void losServiciosIncluidosSonDelVocabularioYNoSeRepiten() {
        DatosDetalle detalle = new DatosDetalle(List.of(Borradores.unidad("Doble", 2, 2, "150000")),
                List.of("wifi", "Jacuzzi", "WiFi"), null, null, null, null, null, null, null, null, null, null, null);

        assertThat(errores(conDetalle(Borradores.alojamiento(), detalle))).containsOnly(
                entry("detalleCategoria.serviciosIncluidos[1]", MotivoInvalido.NO_PERMITIDO),
                entry("detalleCategoria.serviciosIncluidos[2]", MotivoInvalido.DUPLICADO));
    }

    @Test
    void laOfertaDeUnTourExigeDiasYSalidasValidos() {
        assertThat(errores(conOferta(Borradores.tour(), new DatosOferta(List.of(), null)))).containsOnly(
                entry("oferta.diasOperacion", MotivoInvalido.REQUERIDO),
                entry("oferta.salidas", MotivoInvalido.REQUERIDO));
        assertThat(errores(conOferta(Borradores.tour(),
                new DatosOferta(List.of("Lunes", "Feriado", "Lunes"), List.of("8:00", "24:00", "09:30", "09:30")))))
                .containsOnly(
                        entry("oferta.diasOperacion[1]", MotivoInvalido.NO_PERMITIDO),
                        entry("oferta.diasOperacion[2]", MotivoInvalido.DUPLICADO),
                        entry("oferta.salidas[0]", MotivoInvalido.FORMATO),
                        entry("oferta.salidas[1]", MotivoInvalido.FORMATO),
                        entry("oferta.salidas[3]", MotivoInvalido.DUPLICADO));
        List<String> trece = new ArrayList<>();
        for (int hora = 6; hora < 19; hora++) {
            trece.add(String.format("%02d:00", hora));
        }
        assertThat(errores(conOferta(Borradores.tour(), new DatosOferta(List.of("Lunes"), trece))))
                .containsOnly(entry("oferta.salidas", MotivoInvalido.CANTIDAD_MAXIMA));
    }

    @Test
    void unAlojamientoNoTieneSalidasYGastronomiaYAlquilerNoTienenOferta() {
        assertThat(errores(conOferta(Borradores.alojamiento(), new DatosOferta(List.of("Lunes"), List.of("08:00")))))
                .containsOnly(entry("oferta.salidas", MotivoInvalido.NO_PERMITIDO));
        assertThat(errores(conOferta(Borradores.gastronomia(), new DatosOferta(List.of("Lunes"), null))))
                .containsOnly(entry("oferta", MotivoInvalido.NO_PERMITIDO));
        assertThat(errores(conOferta(Borradores.alquiler(), new DatosOferta(List.of("Lunes"), null))))
                .containsOnly(entry("oferta", MotivoInvalido.NO_PERMITIDO));
    }

    @Test
    void losTextosOpcionalesEnBlancoQuedanSinValor() {
        BorradorServicio b = Borradores.tour();
        ServicioComercial servicio = registrar(new BorradorServicio(b.nombreComercial(), b.categoria(),
                b.descripcion(), b.direccion(), b.coordenadas(), b.contacto(), null, "   ", b.precioBase(),
                new DatosDetalle(null, null, null, null, null, null, "Muelle", new BigDecimal("2"), " ", 5, null,
                        null, null),
                b.oferta()));

        assertThat(servicio.getHorarioAtencion()).isEmpty();
        assertThat(((DetalleTour) servicio.getDetalle()).nivelDificultad()).isNull();
        assertThat(servicio.getCondicionesAccesibilidad()).isEmpty();
    }

    @Test
    void unaJustificacionDeRechazoFueraDeLimitesNoSeAcepta() {
        assertThatThrownBy(() -> new Revision(PRESTADOR, AHORA, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Revision(PRESTADOR, AHORA, "x".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BorradorServicio cambiar(BorradorServicio b, UnaryOperator<BorradorServicio> cambio) {
        return cambio.apply(b);
    }

    private static BorradorServicio conPrecio(BorradorServicio b, String precio) {
        return new BorradorServicio(b.nombreComercial(), b.categoria(), b.descripcion(), b.direccion(),
                b.coordenadas(), b.contacto(), b.condicionesAccesibilidad(), b.horarioAtencion(),
                new BigDecimal(precio), b.detalleCategoria(), b.oferta());
    }

    private static BorradorServicio conDetalle(BorradorServicio b, DatosDetalle detalle) {
        return new BorradorServicio(b.nombreComercial(), b.categoria(), b.descripcion(), b.direccion(),
                b.coordenadas(), b.contacto(), b.condicionesAccesibilidad(), b.horarioAtencion(), b.precioBase(),
                detalle, b.oferta());
    }

    private static BorradorServicio conOferta(BorradorServicio b, DatosOferta oferta) {
        return new BorradorServicio(b.nombreComercial(), b.categoria(), b.descripcion(), b.direccion(),
                b.coordenadas(), b.contacto(), b.condicionesAccesibilidad(), b.horarioAtencion(), b.precioBase(),
                b.detalleCategoria(), oferta);
    }

    /** DEC-318: el registro con fotos guarda las rutas públicas en el orden recibido. */
    @Test
    void conFotosElServicioLlevaLasRutasDeLasImagenesEnOrden() {
        byte[] jpeg = java.util.Arrays.copyOf(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 32);
        java.util.Iterator<String> ids = List.of("6a0000000000000000000a01", "6a0000000000000000000a02").iterator();

        RegistroServicio.Registro registro = RegistroServicio.registrarConImagenes(Borradores.tour(),
                List.of(new ImagenSubida("a.jpg", jpeg), new ImagenSubida("b.jpg", jpeg)), Unidades.nuevoId(),
                Unidades::nuevoId, ids::next, PRESTADOR, "RNT-777", true, AHORA);

        assertThat(registro.servicio().getImagenes()).containsExactly("/api/v1/catalogo/imagenes/6a0000000000000000000a01",
                "/api/v1/catalogo/imagenes/6a0000000000000000000a02");
        assertThat(registro.imagenes()).extracting(RegistroAtractivo.ImagenPorGuardar::nombre).containsExactly("a.jpg", "b.jpg");
    }

    /** Los errores de las fotos van en el mismo 400 que los de los datos, y sin imágenes válidas no se pide ningún id. */
    @Test
    void sinFotosOConUnArchivoQueNoEsImagenSonErroresJuntoConLosDeLosDatos() {
        BorradorServicio b = Borradores.tour();
        BorradorServicio sinNombre = new BorradorServicio(null, b.categoria(), b.descripcion(), b.direccion(),
                b.coordenadas(), b.contacto(), b.condicionesAccesibilidad(), b.horarioAtencion(), b.precioBase(),
                b.detalleCategoria(), b.oferta());

        for (List<ImagenSubida> imagenes : List.of(List.<ImagenSubida>of(),
                List.of(new ImagenSubida("texto.txt", "hola".getBytes())))) {
            try {
                RegistroServicio.registrarConImagenes(sinNombre, imagenes, Unidades.nuevoId(), Unidades::nuevoId,
                        () -> {
                            throw new AssertionError("no debe pedir ids");
                        }, PRESTADOR, "RNT-777", true, AHORA);
                throw new AssertionError("Se esperaba ContenidoInvalidoException");
            } catch (ContenidoInvalidoException e) {
                assertThat(e.getCampos()).containsKey("nombreComercial");
                assertThat(e.getCampos()).containsAnyOf(entry("imagenes", MotivoInvalido.REQUERIDO),
                        entry("imagenes[0]", MotivoInvalido.FORMATO));
            }
        }
    }
}
