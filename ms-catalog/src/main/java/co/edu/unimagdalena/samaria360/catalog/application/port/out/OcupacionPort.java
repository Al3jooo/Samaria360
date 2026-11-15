package co.edu.unimagdalena.samaria360.catalog.application.port.out;

import co.edu.unimagdalena.samaria360.catalog.domain.model.AtractivoOcupacion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Ocupación de los atractivos (RF39, RF08, RF51; M016, DEC-304): {@code ocupacionActual} de {@code atractivos} y el
 * historial en {@code ocupaciones}. {@link #registrar} corre dentro de la transacción del caso de uso, con el outbox.
 */
public interface OcupacionPort {

    Optional<AtractivoOcupacion> buscar(String id);

    /** Todos los atractivos de una categoría (los candidatos a alternativos; la colección es pequeña). */
    List<AtractivoOcupacion> deCategoria(String categoria);

    /** Fija la ocupación actual y agrega la lectura al historial. {@code false} si el atractivo ya no existe. */
    boolean registrar(String id, int ocupacion, int limite, Instant fecha, UUID gestor);
}
