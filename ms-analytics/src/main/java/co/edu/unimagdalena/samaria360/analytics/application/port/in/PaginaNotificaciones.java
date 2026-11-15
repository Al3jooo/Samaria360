package co.edu.unimagdalena.samaria360.analytics.application.port.in;

import co.edu.unimagdalena.samaria360.analytics.domain.model.Notificacion;

import java.util.List;

/** Una página de notificaciones del usuario (RF63), de la más reciente a la más antigua, con las no leídas. */
public record PaginaNotificaciones(List<Notificacion> elementos, int pagina, int tamano, long totalElementos,
                                   long noLeidas) {
}
