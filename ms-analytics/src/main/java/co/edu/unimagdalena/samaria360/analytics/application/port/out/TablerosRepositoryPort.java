package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.AfluenciaAtractivo;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.AfluenciaDia;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.AtractivoReporte;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.EntidadConsultada;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.TablerosUseCase.VisitantesZona;
import co.edu.unimagdalena.samaria360.analytics.domain.model.TipoEntidad;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lecturas agregadas de las vistas de analytics_db para los tableros (DEC-308): {@code reservas}, {@code consultas},
 * {@code cuentas} y {@code ocupaciones}. Los rangos son de días de Santa Marta, {@code hasta} incluido.
 */
public interface TablerosRepositoryPort {

    /** Reservas confirmadas (creadas) por día, aunque después se cancelen. Solo los días con reservas. */
    Map<LocalDate, Long> reservasPorDia(LocalDate desde, LocalDate hasta);

    /** Las entidades más consultadas del tipo, de mayor a menor. */
    List<EntidadConsultada> masConsultadas(TipoEntidad tipo, LocalDate desde, LocalDate hasta, int limite);

    /** Turistas registrados por país de origen (decisión 6 del equipo: «zona» es el país). */
    List<VisitantesZona> turistasPorPais();

    /** Consultas a las fichas de los servicios del Prestador. */
    List<EntidadConsultada> consultasPorServicio(UUID prestador, LocalDate desde, LocalDate hasta);

    /** Reservas confirmadas de los servicios del Prestador, con el nombre del servicio. */
    List<EntidadConsultada> reservasPorServicio(UUID prestador, LocalDate desde, LocalDate hasta);

    List<AfluenciaAtractivo> afluenciaPorAtractivo(LocalDate desde, LocalDate hasta, String atractivo);

    List<AfluenciaDia> afluenciaPorDia(LocalDate desde, LocalDate hasta, String atractivo);

    /** Los atractivos con alguna lectura de ocupación, para el filtro de RF64. */
    List<AtractivoReporte> atractivosConLecturas();
}
