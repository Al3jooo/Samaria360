package co.edu.unimagdalena.samaria360.catalog.application.port.out;

import co.edu.unimagdalena.samaria360.catalog.domain.model.ModificacionServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.PaginaResultados;

import java.time.Instant;
import java.util.UUID;

/** Colección {@code historial_modificaciones} (M017; RF41, DEC-311). */
public interface HistorialModificacionesPort {

    /** Idempotente por {@code idEvento} cuando lo trae. */
    void registrar(ModificacionServicio modificacion);

    /** Las del Prestador desde {@code desde}, de la más reciente a la más antigua; {@code idServicio} opcional. */
    PaginaResultados<ModificacionServicio> listar(UUID prestador, String idServicio, Instant desde, int pagina,
                                                  int tamano);
}
