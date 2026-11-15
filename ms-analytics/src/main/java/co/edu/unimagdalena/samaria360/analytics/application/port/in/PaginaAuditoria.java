package co.edu.unimagdalena.samaria360.analytics.application.port.in;

import co.edu.unimagdalena.samaria360.analytics.domain.model.RegistroAuditoria;

import java.util.List;

/** Una página del log de auditoría, de la entrada más reciente a la más antigua. */
public record PaginaAuditoria(List<RegistroAuditoria> elementos, int pagina, int tamano, long totalElementos) {
}
