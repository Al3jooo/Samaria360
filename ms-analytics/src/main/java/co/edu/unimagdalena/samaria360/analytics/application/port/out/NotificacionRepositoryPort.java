package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import co.edu.unimagdalena.samaria360.analytics.domain.model.Notificacion;

import java.util.List;
import java.util.UUID;

/** Colección {@code notificaciones} (RF63). */
public interface NotificacionRepositoryPort {

    void guardar(Notificacion notificacion);

    List<Notificacion> listar(UUID usuario, int pagina, int tamano);

    long contar(UUID usuario);

    long contarNoLeidas(UUID usuario);

    /** @return false si no existe o no es del usuario */
    boolean marcarLeida(UUID usuario, String idNotificacion);

    long marcarTodas(UUID usuario);
}
