package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.FiltroAuditoria;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.PaginaAuditoria;
import co.edu.unimagdalena.samaria360.analytics.domain.model.RegistroAuditoria;

/** Colección {@code logs_auditoria} (RF13). */
public interface AuditoriaRepositoryPort {

    /** Idempotente por {@code idEvento}. */
    void registrar(RegistroAuditoria registro);

    PaginaAuditoria listar(FiltroAuditoria filtro, int pagina, int tamano);
}
