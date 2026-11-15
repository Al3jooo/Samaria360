package co.edu.unimagdalena.samaria360.catalog.application.service;

import co.edu.unimagdalena.samaria360.catalog.application.port.out.HistorialModificacionesPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ModificacionServicio;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.ActualizarServicioUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.GestionServiciosPort;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.EstadoServicioCambiadoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ServicioModificadoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ServicioNoEncontradoException;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ActualizacionDatosComerciales;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ActualizacionPrecio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ActualizacionPrecio.PrecioUnidad;
import co.edu.unimagdalena.samaria360.catalog.domain.model.BorradorServicio.DatosContacto;
import co.edu.unimagdalena.samaria360.catalog.domain.model.IdentificadorObjeto;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioComercial;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioEditado;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * CU «Actualizar tarifas» (RF32) y «Actualizar datos comerciales» (RF43, parte del servicio). Orden: el servicio del
 * Prestador (404), su estado (409 si está Rechazado), los datos (400) y la escritura condicional sobre la versión leída
 * (DEC-186, como DEC-128). Si la escritura no coincide, se vuelve a leer para distinguir el 404, el 409 de estado y el
 * 409 de versión.
 */
public class ActualizarServicioService implements ActualizarServicioUseCase {

    private final GestionServiciosPort servicios;
    private final HistorialModificacionesPort historial;
    private final RelojPort reloj;

    /** Sin historial de precios (los dobles de las pruebas anteriores a RF41). */
    public ActualizarServicioService(GestionServiciosPort servicios) {
        this(servicios, null, null);
    }

    /** @param historial RF41 (DEC-311): cada cambio de precio queda con el valor anterior y el nuevo */
    public ActualizarServicioService(GestionServiciosPort servicios, HistorialModificacionesPort historial,
                                     RelojPort reloj) {
        this.servicios = Objects.requireNonNull(servicios);
        this.historial = historial;
        this.reloj = reloj;
    }

    @Override
    public ServicioComercial actualizarPrecio(UUID prestadorId, String id, BigDecimal precioBase,
                                              List<PrecioUnidad> unidades, Integer version) {
        ServicioComercial antes = historial == null ? null : delPrestador(prestadorId, id);
        ServicioComercial despues = editar(prestadorId, id,
                servicio -> ActualizacionPrecio.aplicar(servicio, precioBase, unidades, version),
                servicios::actualizarPrecio);
        if (historial != null) {
            ModificacionServicio.dePrecio(antes, despues, reloj.ahora()).forEach(historial::registrar);
        }
        return despues;
    }

    @Override
    public ServicioComercial actualizarDatosComerciales(UUID prestadorId, String id, String nombreComercial,
                                                        DatosContacto contacto, Integer version) {
        return editar(prestadorId, id,
                servicio -> ActualizacionDatosComerciales.aplicar(servicio, nombreComercial, contacto, version),
                servicios::actualizarDatosComerciales);
    }

    private ServicioComercial editar(UUID prestadorId, String id, Function<ServicioComercial, ServicioEditado> edicion,
                                     Predicate<ServicioEditado> escritura) {
        ServicioComercial actual = delPrestador(prestadorId, id);
        exigirEditable(actual);
        if (!escritura.test(edicion.apply(actual))) {
            ServicioComercial ahora = delPrestador(prestadorId, id);
            exigirEditable(ahora);
            throw new ServicioModificadoException(ahora.getVersion());
        }
        return delPrestador(prestadorId, id);
    }

    /** Un servicio de otro prestador es, para este, inexistente (DEC-143). */
    private ServicioComercial delPrestador(UUID prestadorId, String id) {
        if (!IdentificadorObjeto.esValido(id)) {
            throw new ServicioNoEncontradoException(id);
        }
        return servicios.buscar(id)
                .filter(servicio -> servicio.getPrestadorId().equals(prestadorId))
                .orElseThrow(() -> new ServicioNoEncontradoException(id));
    }

    private static void exigirEditable(ServicioComercial servicio) {
        if (!servicio.esEditablePorElPrestador()) {
            throw new EstadoServicioCambiadoException(servicio.getEstadoPublicacion());
        }
    }
}
