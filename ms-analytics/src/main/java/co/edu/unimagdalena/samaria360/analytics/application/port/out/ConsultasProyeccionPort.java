package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import co.edu.unimagdalena.samaria360.analytics.domain.model.ConsultasFicha;

/** Colección {@code consultas} de analytics_db (A004; DEC-308): consultas por ficha y día. */
public interface ConsultasProyeccionPort {

    /** Suma las consultas del día a la entidad (crea el documento si no existe). Dentro de la transacción del evento. */
    void sumar(ConsultasFicha consultas);
}
