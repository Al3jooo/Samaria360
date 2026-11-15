package co.edu.unimagdalena.samaria360.chatbot.application.port.out;

import co.edu.unimagdalena.samaria360.chatbot.domain.model.Itinerario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code itinerarios_sugeridos} (Entregable 2, §2.4; RF35). */
public interface ItinerarioRepositoryPort {

    List<Itinerario> listar(UUID turista);

    Optional<Itinerario> buscar(UUID turista, String id);

    long contar(UUID turista);

    Itinerario guardar(Itinerario itinerario);

    boolean eliminar(UUID turista, String id);
}
