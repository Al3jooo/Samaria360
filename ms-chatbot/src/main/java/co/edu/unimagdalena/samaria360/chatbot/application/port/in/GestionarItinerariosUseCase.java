package co.edu.unimagdalena.samaria360.chatbot.application.port.in;

import co.edu.unimagdalena.samaria360.chatbot.domain.model.Itinerario;

import java.util.List;
import java.util.UUID;

/** RF35: el Turista guarda un itinerario del asistente en su perfil y lo consulta o modifica sin regenerarlo. */
public interface GestionarItinerariosUseCase {

    int MAXIMO_POR_TURISTA = 20;

    List<Itinerario> listar(UUID turista);

    Itinerario obtener(UUID turista, String id);

    Itinerario guardar(UUID turista, String nombre, List<Itinerario.Item> items);

    Itinerario actualizar(UUID turista, String id, String nombre, List<Itinerario.Item> items);

    void eliminar(UUID turista, String id);
}
