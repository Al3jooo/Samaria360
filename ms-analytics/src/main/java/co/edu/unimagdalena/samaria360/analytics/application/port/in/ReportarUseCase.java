package co.edu.unimagdalena.samaria360.analytics.application.port.in;

import co.edu.unimagdalena.samaria360.analytics.domain.model.Reporte;

import java.util.UUID;

/** RF57 (DEC-301): el Turista reporta; el Administrador los ve en el panel de moderación y los marca revisados. */
public interface ReportarUseCase {

    int TAMANO = 20;

    record ComandoReportar(UUID turista, String tipoEntidad, String idEntidad, String motivo, String descripcion) {
    }

    Reporte reportar(ComandoReportar comando);

    Pagina<Reporte> listar(String estado, int pagina);

    Reporte marcarRevisado(String id, UUID administrador);
}
