package co.edu.unimagdalena.samaria360.booking.application.service;

import co.edu.unimagdalena.samaria360.booking.application.port.in.GestionarOfertaPrestadorUseCase;
import co.edu.unimagdalena.samaria360.booking.application.port.out.CatalogoPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.InventarioPrestadorPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.OutboxModificacionesPort;
import co.edu.unimagdalena.samaria360.booking.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.booking.domain.exception.CuposReservadosException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.DatosReservaInvalidosException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.FechaConReservasException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.FechaNoDisponibleException;
import co.edu.unimagdalena.samaria360.booking.domain.exception.OfertaNoDisponibleException;
import co.edu.unimagdalena.samaria360.booking.domain.model.CategoriaReservable;
import co.edu.unimagdalena.samaria360.booking.domain.model.FilaPrestador;
import co.edu.unimagdalena.samaria360.booking.domain.model.HorarioReservas;
import co.edu.unimagdalena.samaria360.booking.domain.model.ModificacionOferta;
import co.edu.unimagdalena.samaria360.booking.domain.model.MotivoInvalido;
import co.edu.unimagdalena.samaria360.booking.domain.model.OfertaReservable;
import co.edu.unimagdalena.samaria360.booking.domain.service.MaterializacionOferta;
import co.edu.unimagdalena.samaria360.booking.domain.service.PoliticaCambioOferta;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * RF19 (decisión 3 del equipo, respuesta 3; DEC-310). Cada operación tiene dos fases, como reservar (ADR-007):
 * {@link #preparar} lee el catálogo (el servicio debe ser del Prestador del token; si no, 404 como si no existiera) y
 * materializa la ventana <b>fuera</b> de una transacción; {@code aplicar…} hace el cambio condicional y encola
 * {@code OfertaModificada} dentro de una (la abre la infraestructura).
 */
public class GestionarOfertaPrestadorService implements GestionarOfertaPrestadorUseCase {

    private final CatalogoPort catalogo;
    private final MaterializarInventarioService materializar;
    private final InventarioPrestadorPort inventario;
    private final OutboxModificacionesPort outbox;
    private final RelojPort reloj;
    private final PoliticaCambioOferta politica = new PoliticaCambioOferta();

    public GestionarOfertaPrestadorService(CatalogoPort catalogo, MaterializarInventarioService materializar,
                                           InventarioPrestadorPort inventario, OutboxModificacionesPort outbox,
                                           RelojPort reloj) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.materializar = Objects.requireNonNull(materializar);
        this.inventario = Objects.requireNonNull(inventario);
        this.outbox = Objects.requireNonNull(outbox);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /** Sin transacción: el servicio del Prestador, con la ventana materializada hasta {@code hasta}. */
    public OfertaReservable preparar(UUID prestador, String idServicio, LocalDate hasta) {
        OfertaReservable oferta = catalogo.buscarReservable(idServicio)
                .filter(o -> prestador.equals(o.idPrestador()))
                .orElseThrow(() -> new OfertaNoDisponibleException(idServicio));
        politica.categoria(oferta);
        materializar.asegurar(oferta, hasta == null ? hoy() : hasta);
        return oferta;
    }

    private LocalDate hoy() {
        return HorarioReservas.hoy(reloj.ahora());
    }

    @Override
    public Inventario inventario(UUID prestador, String idServicio, LocalDate desde, LocalDate hasta) {
        LocalDate manana = hoy().plusDays(1);
        LocalDate inicio = desde == null ? manana : desde;
        LocalDate fin = hasta == null ? inicio.plusDays(13) : hasta;
        if (fin.isBefore(inicio)) {
            throw new DatosReservaInvalidosException(Map.of("hasta", MotivoInvalido.RANGO_INVALIDO));
        }
        if (fin.isAfter(MaterializacionOferta.ultimoDia(hoy()))) {
            fin = MaterializacionOferta.ultimoDia(hoy());
        }
        OfertaReservable oferta = preparar(prestador, idServicio, fin);
        List<String> dias = oferta.oferta() == null ? List.of()
                : oferta.oferta().dias().stream().sorted().map(PoliticaCambioOferta::nombre).toList();
        List<String> salidas = oferta.oferta() == null ? List.of()
                : oferta.oferta().salidas().stream().map(LocalTime::toString).toList();
        return new Inventario(idServicio, oferta.nombre(), oferta.categoria(), inicio, fin, dias, salidas,
                inventario.listar(idServicio, inicio, fin));
    }

    @Override
    public FilaPrestador ajustar(ComandoFila comando) {
        return aplicarAjuste(preparar(comando.prestador(), comando.idServicio(), comando.fecha()), comando);
    }

    @Override
    public FilaPrestador cerrar(ComandoFila comando) {
        return aplicarCierre(preparar(comando.prestador(), comando.idServicio(), comando.fecha()), comando);
    }

    @Override
    public void cambiarPlantilla(ComandoPlantilla comando) {
        aplicarPlantilla(preparar(comando.prestador(), comando.idServicio(), null), comando);
    }

    /** En la transacción: ajusta la fila (o la abre) y encola el cambio. */
    public FilaPrestador aplicarAjuste(OfertaReservable oferta, ComandoFila comando) {
        politica.validarFila(oferta, comando.fecha(), comando.horaInicio(), comando.idUnidad(), hoy());
        int cupos = politica.cupos(comando.cuposTotales());
        CategoriaReservable categoria = politica.categoria(oferta);
        var actual = inventario.buscar(oferta.idServicio(), comando.idUnidad(), comando.fecha(), comando.horaInicio());
        ModificacionOferta.Tipo tipo;
        String anterior;
        if (actual.isPresent()) {
            FilaPrestador fila = actual.get();
            if (!inventario.ajustar(oferta.idServicio(), comando.idUnidad(), comando.fecha(), comando.horaInicio(),
                    cupos)) {
                throw new CuposReservadosException(fila.reservados());
            }
            tipo = fila.cerrado() ? ModificacionOferta.Tipo.Apertura : ModificacionOferta.Tipo.Cupos;
            anterior = fila.cerrado() ? "Cerrado" : String.valueOf(fila.cuposTotales());
        } else {
            if (!inventario.insertar(oferta.idServicio(), categoria, comando.idUnidad(), comando.fecha(),
                    comando.horaInicio(), cupos)) {
                // Otro cambio la creó en el mismo instante: se reintenta como ajuste.
                return aplicarAjuste(oferta, comando);
            }
            tipo = ModificacionOferta.Tipo.Apertura;
            anterior = null;
        }
        outbox.registrar(new ModificacionOferta(oferta.idServicio(), oferta.idPrestador(), tipo, comando.fecha(),
                comando.horaInicio(), comando.idUnidad(), anterior, String.valueOf(cupos), null, null, reloj.ahora()));
        return inventario.buscar(oferta.idServicio(), comando.idUnidad(), comando.fecha(), comando.horaInicio())
                .orElseThrow();
    }

    /** En la transacción: cierra la fila si no tiene reservas y encola el cambio. */
    public FilaPrestador aplicarCierre(OfertaReservable oferta, ComandoFila comando) {
        politica.validarFila(oferta, comando.fecha(), comando.horaInicio(), comando.idUnidad(), hoy());
        FilaPrestador fila = inventario.buscar(oferta.idServicio(), comando.idUnidad(), comando.fecha(),
                        comando.horaInicio())
                .filter(f -> !f.cerrado())
                .orElseThrow(() -> new FechaNoDisponibleException());
        if (!inventario.cerrar(oferta.idServicio(), comando.idUnidad(), comando.fecha(), comando.horaInicio())) {
            throw new FechaConReservasException();
        }
        outbox.registrar(new ModificacionOferta(oferta.idServicio(), oferta.idPrestador(),
                ModificacionOferta.Tipo.Cierre, comando.fecha(), comando.horaInicio(), comando.idUnidad(),
                String.valueOf(fila.cuposTotales()), "Cerrado", null, null, reloj.ahora()));
        return inventario.buscar(oferta.idServicio(), comando.idUnidad(), comando.fecha(), comando.horaInicio())
                .orElseThrow();
    }

    /** En la transacción: encola la plantilla nueva; ms-catalog la aplica en la oferta (respuesta 3 del equipo). */
    public void aplicarPlantilla(OfertaReservable oferta, ComandoPlantilla comando) {
        PoliticaCambioOferta.Plantilla plantilla = politica.plantilla(oferta, comando.diasOperacion(),
                comando.salidas());
        List<String> dias = plantilla.dias().stream().sorted().map(PoliticaCambioOferta::nombre).toList();
        List<String> salidas = plantilla.salidas().stream().map(LocalTime::toString).toList();
        String anterior = oferta.oferta() == null ? null : texto(
                oferta.oferta().dias().stream().sorted().map(PoliticaCambioOferta::nombre).toList(),
                oferta.oferta().salidas().stream().map(LocalTime::toString).toList());
        String nuevo = texto(dias, salidas);
        if (nuevo.equals(anterior)) {
            return; // Sin cambios: ni evento ni entrada en el historial (RF41).
        }
        outbox.registrar(new ModificacionOferta(oferta.idServicio(), oferta.idPrestador(),
                ModificacionOferta.Tipo.Plantilla, null, null, null, anterior, nuevo, dias, salidas,
                reloj.ahora()));
    }

    /** Los nombres del catálogo van sin tildes (contrato); el texto del historial los muestra con ellas. */
    private static final java.util.Map<String, String> DIAS_LEGIBLES = java.util.Map.of(
            "Miercoles", "Miércoles", "Sabado", "Sábado");

    private static String texto(List<String> dias, List<String> salidas) {
        List<String> legibles = dias.stream().map(d -> DIAS_LEGIBLES.getOrDefault(d, d)).toList();
        return String.join(", ", legibles) + (salidas.isEmpty() ? "" : " · " + String.join(", ", salidas));
    }
}
