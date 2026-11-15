package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import co.edu.unimagdalena.samaria360.analytics.domain.model.PromedioCalificacion;

/** Colección {@code promedios} (A002): el último promedio visible por entidad y su versión monótona (DEC-300). */
public interface PromedioRepositoryPort {

    /** Guarda el promedio, sube la versión y la devuelve (desde 1). Dentro de la transacción del caso de uso. */
    long guardar(PromedioCalificacion promedio);
}
