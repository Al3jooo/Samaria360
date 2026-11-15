package co.edu.unimagdalena.samaria360.catalog.application.port.out;

import co.edu.unimagdalena.samaria360.catalog.domain.model.CalificacionEntidad;

/** El campo {@code calificacion} de {@code servicios} y {@code atractivos} (M015; DEC-302). */
public interface CalificacionCatalogoPort {

    /**
     * UPDATE condicional: solo si la entidad existe y no tiene calificación o su versión es menor. {@code true} si
     * cambió.
     */
    boolean guardarSiMayor(CalificacionEntidad calificacion);
}
