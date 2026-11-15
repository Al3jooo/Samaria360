package co.edu.unimagdalena.samaria360.catalog.infrastructure.config;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.ActualizarServicioUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.OfertaServicioUseCase;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ActualizacionPrecio.PrecioUnidad;
import co.edu.unimagdalena.samaria360.catalog.domain.model.BorradorServicio.DatosContacto;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ImagenSubida;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ModificacionServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.PaginaResultados;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioComercial;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * RF41 (DEC-311) en transacciones de MongoDB: el cambio de precio (RF32) con su entrada del historial, y la plantilla
 * de ms-booking (RF19) con la suya, se confirman juntos. La galería (RF48) no la necesita: compensa con GridFS.
 */
public final class HistorialTransaccional {

    private HistorialTransaccional() {
    }

    public static ActualizarServicioUseCase edicion(ActualizarServicioUseCase delegado, TransactionTemplate tx) {
        return new ActualizarServicioUseCase() {
            @Override
            public ServicioComercial actualizarPrecio(UUID prestadorId, String id, BigDecimal precioBase,
                                                      List<PrecioUnidad> unidades, Integer version) {
                return tx.execute(estado -> delegado.actualizarPrecio(prestadorId, id, precioBase, unidades, version));
            }

            @Override
            public ServicioComercial actualizarDatosComerciales(UUID prestadorId, String id, String nombreComercial,
                                                                DatosContacto contacto, Integer version) {
                return delegado.actualizarDatosComerciales(prestadorId, id, nombreComercial, contacto, version);
            }
        };
    }

    public static OfertaServicioUseCase oferta(OfertaServicioUseCase delegado, TransactionTemplate tx) {
        return new OfertaServicioUseCase() {
            @Override
            public void registrarCambio(CambioRecibido cambio) {
                tx.executeWithoutResult(estado -> delegado.registrarCambio(cambio));
            }

            @Override
            public PaginaResultados<ModificacionServicio> historial(UUID prestador, String idServicio, int pagina) {
                return delegado.historial(prestador, idServicio, pagina);
            }

            @Override
            public List<String> agregarImagen(UUID prestador, String idServicio, ImagenSubida imagen) {
                return delegado.agregarImagen(prestador, idServicio, imagen);
            }

            @Override
            public List<String> quitarImagen(UUID prestador, String idServicio, String ruta) {
                return delegado.quitarImagen(prestador, idServicio, ruta);
            }
        };
    }
}
