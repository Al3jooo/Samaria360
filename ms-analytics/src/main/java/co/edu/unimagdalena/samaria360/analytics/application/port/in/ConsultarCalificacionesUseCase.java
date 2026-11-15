package co.edu.unimagdalena.samaria360.analytics.application.port.in;

import co.edu.unimagdalena.samaria360.analytics.domain.model.Calificacion;
import co.edu.unimagdalena.samaria360.analytics.domain.model.PromedioCalificacion;

import java.util.List;

/** RF17 (las visibles de una entidad, públicas) y RF56 (las de los servicios del Prestador). */
public interface ConsultarCalificacionesUseCase {

    int TAMANO_PUBLICO = 10;
    int TAMANO_PRESTADOR = 20;

    record CalificacionesEntidad(PromedioCalificacion promedio, Pagina<Calificacion> pagina) {
    }

    record CalificacionesPrestador(PromedioCalificacion promedio, List<ResumenServicio> servicios,
                                   Pagina<Calificacion> pagina) {
    }

    CalificacionesEntidad deEntidad(String tipoEntidad, String idEntidad, int pagina);

    CalificacionesPrestador delPrestador(FiltroCalificacionesPrestador filtro, int pagina);
}
