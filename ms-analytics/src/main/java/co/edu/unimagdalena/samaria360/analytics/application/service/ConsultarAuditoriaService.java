package co.edu.unimagdalena.samaria360.analytics.application.service;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.ConsultarAuditoriaUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.FiltroAuditoria;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.PaginaAuditoria;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.AuditoriaRepositoryPort;

import java.util.Objects;

/** Log de auditoría (RF13) para el Administrador, de la entrada más reciente a la más antigua, en páginas de 20. */
public class ConsultarAuditoriaService implements ConsultarAuditoriaUseCase {

    private final AuditoriaRepositoryPort auditoria;

    public ConsultarAuditoriaService(AuditoriaRepositoryPort auditoria) {
        this.auditoria = Objects.requireNonNull(auditoria);
    }

    @Override
    public PaginaAuditoria listar(FiltroAuditoria filtro, int pagina) {
        return auditoria.listar(filtro, Math.max(0, pagina), TAMANO_PAGINA);
    }
}
