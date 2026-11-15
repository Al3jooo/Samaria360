package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import co.edu.unimagdalena.samaria360.analytics.domain.model.ConsultasFicha;
import co.edu.unimagdalena.samaria360.analytics.domain.model.TipoEntidad;

import java.util.Optional;

/** Colección {@code consultas} de analytics_db (A004; DEC-308): consultas por ficha y día. */
public interface ConsultasProyeccionPort {

    /** Suma las consultas del día a la entidad (crea el documento si no existe). Dentro de la transacción del evento. */
    void sumar(ConsultasFicha consultas);

    /**
     * El nombre más reciente de la ficha que trajo {@code ConsultasAgregadas} (DEC-307), o vacío si nunca se consultó.
     * Lo usa la moderación (DEC-323): para calificar, el turista abrió la ficha, así que su nombre ya llegó.
     */
    Optional<String> ultimoNombre(TipoEntidad tipo, String idEntidad);
}
