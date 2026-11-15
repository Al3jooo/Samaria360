package co.edu.unimagdalena.samaria360.chatbot.application.service;

import co.edu.unimagdalena.samaria360.chatbot.application.port.in.GestionarItinerariosUseCase;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.ItinerarioRepositoryPort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.chatbot.domain.exception.LimiteSuperadoException;
import co.edu.unimagdalena.samaria360.chatbot.domain.exception.RecursoNoEncontradoException;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Itinerario;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * RF35: itinerarios en el perfil del Turista; cada turista ve y modifica solo los suyos (el del {@code sub}). Hasta 20
 * por turista (DEC-297). Modificar reemplaza el nombre y los ítems (reordenar, cambiar el día o quitar).
 */
public class GestionarItinerariosService implements GestionarItinerariosUseCase {

    private static final String NO_ENCONTRADO = "ITINERARIO_NO_ENCONTRADO";

    private final ItinerarioRepositoryPort itinerarios;
    private final RelojPort reloj;

    public GestionarItinerariosService(ItinerarioRepositoryPort itinerarios, RelojPort reloj) {
        this.itinerarios = Objects.requireNonNull(itinerarios);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public List<Itinerario> listar(UUID turista) {
        return itinerarios.listar(turista);
    }

    @Override
    public Itinerario obtener(UUID turista, String id) {
        return itinerarios.buscar(turista, id).orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
    }

    @Override
    public Itinerario guardar(UUID turista, String nombre, List<Itinerario.Item> items) {
        Instant ahora = reloj.ahora().truncatedTo(ChronoUnit.MILLIS);
        Itinerario nuevo = Itinerario.validar(null, turista, nombre, items, ahora, ahora);
        if (itinerarios.contar(turista) >= MAXIMO_POR_TURISTA) {
            throw new LimiteSuperadoException("LIMITE_ITINERARIOS");
        }
        return itinerarios.guardar(nuevo);
    }

    @Override
    public Itinerario actualizar(UUID turista, String id, String nombre, List<Itinerario.Item> items) {
        Itinerario actual = obtener(turista, id);
        return itinerarios.guardar(Itinerario.validar(actual.id(), turista, nombre, items, actual.fechaCreacion(),
                reloj.ahora().truncatedTo(ChronoUnit.MILLIS)));
    }

    @Override
    public void eliminar(UUID turista, String id) {
        if (!itinerarios.eliminar(turista, id)) {
            throw new RecursoNoEncontradoException(NO_ENCONTRADO);
        }
    }
}
