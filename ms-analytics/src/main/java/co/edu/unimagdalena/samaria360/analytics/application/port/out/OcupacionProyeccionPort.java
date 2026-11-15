package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import co.edu.unimagdalena.samaria360.analytics.domain.model.OcupacionRegistrada;

/** Colección {@code ocupaciones} de analytics_db (A003; DEC-304): el historial de afluencia para RF64. */
public interface OcupacionProyeccionPort {

    /** Idempotente por {@code idEvento}. */
    void guardar(OcupacionRegistrada ocupacion);
}
