package co.edu.unimagdalena.samaria360.analytics.application.service;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.GestionarNotificacionesUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.PaginaNotificaciones;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.NotificacionRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.domain.exception.NotificacionNoEncontradaException;

import java.util.Objects;
import java.util.UUID;

/** Campana de RF63: cada usuario ve y marca solo las suyas (el usuario es siempre el {@code sub} del token). */
public class GestionarNotificacionesService implements GestionarNotificacionesUseCase {

    private final NotificacionRepositoryPort notificaciones;

    public GestionarNotificacionesService(NotificacionRepositoryPort notificaciones) {
        this.notificaciones = Objects.requireNonNull(notificaciones);
    }

    @Override
    public PaginaNotificaciones listar(UUID usuario, int pagina) {
        int numero = Math.max(0, pagina);
        return new PaginaNotificaciones(notificaciones.listar(usuario, numero, TAMANO_PAGINA), numero, TAMANO_PAGINA,
                notificaciones.contar(usuario), notificaciones.contarNoLeidas(usuario));
    }

    @Override
    public void marcarLeida(UUID usuario, String idNotificacion) {
        if (!notificaciones.marcarLeida(usuario, idNotificacion)) {
            throw new NotificacionNoEncontradaException();
        }
    }

    @Override
    public long marcarTodas(UUID usuario) {
        return notificaciones.marcarTodas(usuario);
    }
}
