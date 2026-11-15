package co.edu.unimagdalena.samaria360.analytics.application.service;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.Pagina;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ReportarUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.CuentaProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.ReporteRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.domain.exception.CamposInvalidosException;
import co.edu.unimagdalena.samaria360.analytics.domain.exception.ReglaNegocioException;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Calificacion;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Reporte;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * RF57 (respuesta 5 del equipo; DEC-301): cualquier turista Activo reporta un servicio o un atractivo con un motivo de
 * la lista; uno pendiente por turista y entidad (409 {@code REPORTE_DUPLICADO}). El Administrador solo lo marca
 * {@code Revisado} (la corrección la hace quien es dueño del dato, fuera de este caso de uso).
 */
public class ReportesService implements ReportarUseCase {

    private final ReporteRepositoryPort reportes;
    private final CuentaProyeccionPort cuentas;
    private final RelojPort reloj;

    public ReportesService(ReporteRepositoryPort reportes, CuentaProyeccionPort cuentas, RelojPort reloj) {
        this.reportes = Objects.requireNonNull(reportes);
        this.cuentas = Objects.requireNonNull(cuentas);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public Reporte reportar(ComandoReportar comando) {
        Reporte nuevo = Reporte.nuevo(comando.tipoEntidad(), comando.idEntidad(), comando.turista(), comando.motivo(),
                comando.descripcion(), reloj.ahora());
        Optional<String> estado = cuentas.estadoDe(comando.turista());
        if (estado.isPresent() && !"Activo".equals(estado.get())) {
            throw new ReglaNegocioException(ReglaNegocioException.CUENTA_INACTIVA);
        }
        if (reportes.existePendiente(comando.turista(), nuevo.tipoEntidad(), nuevo.idEntidad())) {
            throw new ReglaNegocioException(ReglaNegocioException.REPORTE_DUPLICADO);
        }
        return reportes.insertar(nuevo);
    }

    @Override
    public Pagina<Reporte> listar(String estado, int pagina) {
        Reporte.Estado filtro = null;
        if (estado != null && !estado.isBlank()) {
            try {
                filtro = Reporte.Estado.valueOf(estado.trim());
            } catch (IllegalArgumentException e) {
                throw new CamposInvalidosException(Map.of("estado", "NO_PERMITIDO"));
            }
        }
        return reportes.listar(filtro, pagina, TAMANO);
    }

    @Override
    public Reporte marcarRevisado(String id, UUID administrador) {
        if (!Calificacion.esObjectId(id)) {
            throw new ReglaNegocioException(ReglaNegocioException.REPORTE_NO_ENCONTRADO);
        }
        reportes.buscar(id).orElseThrow(() -> new ReglaNegocioException(ReglaNegocioException.REPORTE_NO_ENCONTRADO));
        if (!reportes.marcarRevisado(id, administrador, reloj.ahora())) {
            throw new ReglaNegocioException(ReglaNegocioException.ESTADO_CAMBIADO,
                    Map.of("estadoActual", Reporte.Estado.Revisado.name()));
        }
        return reportes.buscar(id).orElseThrow();
    }
}
