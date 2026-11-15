package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.Pagina;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Reporte;
import co.edu.unimagdalena.samaria360.analytics.domain.model.TipoEntidad;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Colección {@code reportes} de analytics_db (A002; RF57). */
public interface ReporteRepositoryPort {

    boolean existePendiente(UUID turista, TipoEntidad tipo, String idEntidad);

    Reporte insertar(Reporte reporte);

    Optional<Reporte> buscar(String id);

    /** Del más antiguo al más reciente (los pendientes primero se atienden en orden de llegada). */
    Pagina<Reporte> listar(Reporte.Estado estado, int pagina, int tamano);

    /** UPDATE condicional sobre {@code Pendiente}; {@code false} si ya estaba revisado. */
    boolean marcarRevisado(String id, UUID administrador, Instant fecha);
}
