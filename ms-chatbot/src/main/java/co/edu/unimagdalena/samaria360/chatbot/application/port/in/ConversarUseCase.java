package co.edu.unimagdalena.samaria360.chatbot.application.port.in;

import co.edu.unimagdalena.samaria360.chatbot.domain.model.Itinerario;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Sugerencia;

import java.util.List;
import java.util.UUID;

/**
 * RF04: el Turista conversa en lenguaje natural y recibe atractivos, servicios e itinerarios, cada uno con su
 * justificación (RF36), en el idioma de la interfaz (RF59).
 */
public interface ConversarUseCase {

    /** Mensaje del turista: de 1 a 500 caracteres; {@code intereses}, sus categorías declaradas (RF01, RF53). */
    record ComandoMensaje(UUID turistaId, String mensaje, String idioma, List<String> intereses) {
    }

    /**
     * {@code texto}: la respuesta redactada; {@code sugerencias} e {@code itinerario}, calculados con los datos del
     * catálogo; {@code fuentes}, las categorías de la base de conocimientos usadas; {@code mensajesRestantes} del día.
     */
    record RespuestaAsistente(String texto, List<Sugerencia> sugerencias, List<Itinerario.Item> itinerario,
                              List<String> fuentes, boolean catalogoDisponible, int mensajesRestantes) {
    }

    int MENSAJE_MAXIMO = 500;
    int MENSAJES_POR_DIA = 20;

    RespuestaAsistente conversar(ComandoMensaje comando);
}
