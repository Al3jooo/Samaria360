package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.FiltroCalificacionesPrestador;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.Pagina;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ResumenServicio;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Calificacion;
import co.edu.unimagdalena.samaria360.analytics.domain.model.TipoEntidad;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Colección {@code calificaciones} de analytics_db (A002; RF17, RF29, RF56). Las escrituras corren dentro de la
 * transacción de MongoDB del caso de uso: los adaptadores leen antes de escribir (una llave duplicada la abortaría).
 */
public interface CalificacionRepositoryPort {

    boolean existe(UUID turista, TipoEntidad tipo, String idEntidad);

    Optional<Calificacion> deTurista(UUID turista, TipoEntidad tipo, String idEntidad);

    /** Devuelve la calificación con su id. */
    Calificacion insertar(Calificacion calificacion);

    Optional<Calificacion> buscar(String id);

    /** UPDATE condicional sobre el estado esperado; {@code false} si ya no estaba en ese estado. */
    boolean cambiarEstado(String id, Calificacion.Estado esperado, Calificacion.Estado nuevo, String motivo,
                          Instant fecha);

    boolean eliminar(String id);

    /** Suma de estrellas y cantidad de las calificaciones visibles de la entidad. */
    long[] sumaYCantidadVisibles(TipoEntidad tipo, String idEntidad);

    /** Las visibles de la entidad, de la más reciente a la más antigua. */
    Pagina<Calificacion> visibles(TipoEntidad tipo, String idEntidad, int pagina, int tamano);

    /** RF56: las visibles de los servicios del Prestador con sus filtros, de la más reciente a la más antigua. */
    Pagina<Calificacion> delPrestador(FiltroCalificacionesPrestador filtro, int pagina, int tamano);

    /** Suma y cantidad de las visibles del Prestador con los mismos filtros. */
    long[] sumaYCantidadDelPrestador(FiltroCalificacionesPrestador filtro);

    /** Un resumen por servicio calificado del Prestador (visibles), para el filtro de RF56. */
    List<ResumenServicio> serviciosDelPrestador(UUID prestador);

    /** RF29: todas, o las de un estado, de la más reciente a la más antigua. */
    Pagina<Calificacion> paraModerar(Calificacion.Estado estado, int pagina, int tamano);
}
