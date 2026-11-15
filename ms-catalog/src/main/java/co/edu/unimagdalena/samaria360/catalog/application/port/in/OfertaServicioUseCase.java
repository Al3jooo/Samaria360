package co.edu.unimagdalena.samaria360.catalog.application.port.in;

import co.edu.unimagdalena.samaria360.catalog.domain.model.ImagenSubida;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ModificacionServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.PaginaResultados;

import java.util.List;
import java.util.UUID;

/**
 * Iteración I en ms-catalog (DEC-311, DEC-313): el historial de modificaciones del Prestador (RF41), los cambios de
 * oferta que llegan de ms-booking (RF19) y la galería de un servicio aprobado (RF48).
 */
public interface OfertaServicioUseCase {

    int TAMANO_HISTORIAL = 20;

    /** Un {@code OfertaModificada} de ms-booking: al historial y, si es una plantilla, a la oferta. Idempotente. */
    record CambioRecibido(String idEvento, String idServicio, UUID idPrestador, String tipo, String fecha,
                          String horaInicio, String idUnidad, String valorAnterior, String valorNuevo,
                          List<String> diasOperacion, List<String> salidas, java.time.Instant ocurridoEn) {
    }

    void registrarCambio(CambioRecibido cambio);

    /** RF41: los últimos 30 días, de la más reciente a la más antigua; {@code idServicio} opcional. */
    PaginaResultados<ModificacionServicio> historial(UUID prestador, String idServicio, int pagina);

    /** RF48: agrega una imagen JPEG o PNG de hasta 5 MB a la galería de un servicio Publicado; devuelve la galería. */
    List<String> agregarImagen(UUID prestador, String idServicio, ImagenSubida imagen);

    /** RF48: quita una imagen de la galería (le queda al menos una); devuelve la galería. */
    List<String> quitarImagen(UUID prestador, String idServicio, String ruta);
}
