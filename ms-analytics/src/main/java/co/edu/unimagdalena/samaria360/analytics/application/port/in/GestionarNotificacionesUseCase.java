package co.edu.unimagdalena.samaria360.analytics.application.port.in;

import java.util.UUID;

/** Campana de RF63: las notificaciones del usuario autenticado y su lectura. */
public interface GestionarNotificacionesUseCase {

    int TAMANO_PAGINA = 20;

    PaginaNotificaciones listar(UUID usuario, int pagina);

    /** @throws co.edu.unimagdalena.samaria360.analytics.domain.exception.NotificacionNoEncontradaException si no es suya */
    void marcarLeida(UUID usuario, String idNotificacion);

    /** @return cuántas pasaron a leídas */
    long marcarTodas(UUID usuario);
}
