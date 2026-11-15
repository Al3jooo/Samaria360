package co.edu.unimagdalena.samaria360.catalog.application.service;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.RegistrarServicioUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.AlmacenImagenesPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.CuentaRepositoryPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.GestionServiciosPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.PrestadorNoHabilitadoException;
import co.edu.unimagdalena.samaria360.catalog.domain.model.BorradorServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Cuenta;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ImagenSubida;
import co.edu.unimagdalena.samaria360.catalog.domain.model.RegistroAtractivo.ImagenPorGuardar;
import co.edu.unimagdalena.samaria360.catalog.domain.model.RegistroServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioComercial;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * CU «Registrar servicio» (RF09). Primero la cuenta (403, DEC-125) y después los datos (400): un prestador no
 * habilitado no recibe la validación de su formulario.
 * <p>
 * Carrera con RF22 (DEC-142 a): si el prestador se inactiva entre la lectura de la proyección y el {@code insert}, el
 * {@code updateMulti} de la inactivación pudo correr antes de que existiera este servicio. Por eso, después de
 * insertarlo, se relee la proyección y, si ya no está Activa, se fija {@code prestadorActivo = false} en el servicio. La
 * otra mitad está en {@link ActualizarCuentaService}, que vuelve a fijar la visibilidad después de guardar la cuenta.
 */
public class RegistrarServicioService implements RegistrarServicioUseCase {

    private final GestionServiciosPort servicios;
    private final CuentaRepositoryPort cuentas;
    private final RelojPort reloj;
    /** GridFS (ADR-009), para las fotos del registro (DEC-318); nulo solo en las pruebas del registro por JSON. */
    private final AlmacenImagenesPort imagenes;

    public RegistrarServicioService(GestionServiciosPort servicios, CuentaRepositoryPort cuentas, RelojPort reloj) {
        this(servicios, cuentas, reloj, null);
    }

    public RegistrarServicioService(GestionServiciosPort servicios, CuentaRepositoryPort cuentas, RelojPort reloj,
                                    AlmacenImagenesPort imagenes) {
        this.servicios = Objects.requireNonNull(servicios);
        this.cuentas = Objects.requireNonNull(cuentas);
        this.reloj = Objects.requireNonNull(reloj);
        this.imagenes = imagenes;
    }

    /** La fecha va en milisegundos, como la guarda MongoDB. */
    @Override
    public ServicioComercial registrar(UUID prestadorId, BorradorServicio borrador) {
        Cuenta cuenta = cuenta(prestadorId);
        ServicioComercial servicio = RegistroServicio.registrar(borrador, servicios.nuevoId(), servicios::nuevoId,
                prestadorId, cuenta.rnt(), cuenta.prestadorActivo(), reloj.ahora().truncatedTo(ChronoUnit.MILLIS));
        servicios.insertar(servicio);
        return confirmarVisibilidad(prestadorId, servicio);
    }

    /**
     * DEC-318: valida todo, guarda las imágenes y al final el servicio; si algo falla, borra las imágenes guardadas
     * (compensación, como el registro de atractivos; el riesgo de huérfanos está en ADR-009).
     */
    @Override
    public ServicioComercial registrar(UUID prestadorId, BorradorServicio borrador, List<ImagenSubida> subidas) {
        Objects.requireNonNull(imagenes, "Sin almacén de imágenes");
        Cuenta cuenta = cuenta(prestadorId);
        RegistroServicio.Registro registro = RegistroServicio.registrarConImagenes(borrador, subidas,
                servicios.nuevoId(), servicios::nuevoId, imagenes::nuevoId, prestadorId, cuenta.rnt(),
                cuenta.prestadorActivo(), reloj.ahora().truncatedTo(ChronoUnit.MILLIS));
        List<String> guardadas = new ArrayList<>();
        try {
            for (ImagenPorGuardar imagen : registro.imagenes()) {
                imagenes.guardar(imagen.id(), imagen.formato(), imagen.nombre(), imagen.contenido());
                guardadas.add(imagen.id());
            }
            servicios.insertar(registro.servicio());
        } catch (RuntimeException e) {
            for (String id : guardadas) {
                try {
                    imagenes.borrar(id);
                } catch (RuntimeException otra) {
                    e.addSuppressed(otra);
                }
            }
            throw e;
        }
        return confirmarVisibilidad(prestadorId, registro.servicio());
    }

    private Cuenta cuenta(UUID prestadorId) {
        return cuentas.buscar(prestadorId)
                .filter(Cuenta::puedeRegistrarServicios)
                .orElseThrow(PrestadorNoHabilitadoException::new);
    }

    private ServicioComercial confirmarVisibilidad(UUID prestadorId, ServicioComercial servicio) {
        boolean sigueActivo = cuentas.buscar(prestadorId).map(Cuenta::prestadorActivo).orElse(false);
        if (!sigueActivo) {
            String id = servicio.getId().orElseThrow();
            servicios.fijarPrestadorActivoEnServicio(id, false);
            return servicio.conPrestadorActivo(false);
        }
        return servicio;
    }
}
