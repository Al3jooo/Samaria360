package co.edu.unimagdalena.samaria360.analytics.application.port.in;

/** Log de auditoría para el Administrador (RF13). */
public interface ConsultarAuditoriaUseCase {

    int TAMANO_PAGINA = 20;

    PaginaAuditoria listar(FiltroAuditoria filtro, int pagina);
}
