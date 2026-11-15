package co.edu.unimagdalena.samaria360.chatbot.application.port.out;

import co.edu.unimagdalena.samaria360.chatbot.domain.model.EntradaConocimiento;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Idioma;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Itinerario;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Sugerencia;

import java.util.List;

/**
 * Redacción de la respuesta del asistente (decisión 5 del equipo; DEC-279): dos adaptadores, Reglas (por defecto, sin
 * llave, para el CI) y Gemini (con {@code GEMINI_API_KEY}). El modelo solo recibe el mensaje, las sugerencias del
 * catálogo y la base de conocimientos: nada personal (ni el id, ni el nombre, ni los intereses del turista).
 */
public interface ModeloLenguajePort {

    record ContextoRespuesta(Idioma idioma, String mensaje, List<Sugerencia> sugerencias,
                             List<EntradaConocimiento> conocimiento, List<Itinerario.Item> itinerario,
                             boolean catalogoDisponible) {
    }

    /** Nunca falla: si el proveedor externo no responde, el adaptador redacta con las reglas. */
    String redactar(ContextoRespuesta contexto);
}
