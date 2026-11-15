package co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.out.modelo;

import co.edu.unimagdalena.samaria360.chatbot.application.port.out.ModeloLenguajePort;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.EntradaConocimiento;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Idioma;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Itinerario;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Sugerencia;

import java.util.List;

/**
 * Redactor por reglas (decisión 5 del equipo): el adaptador por defecto, sin llave ni proveedor externo, el del CI. Arma
 * la respuesta con plantillas en español o inglés (RF59) y solo con el contexto recibido; si no hay nada, lo dice.
 * También es el respaldo del adaptador de Gemini.
 */
public class RedactorReglasAdapter implements ModeloLenguajePort {

    static final int CONOCIMIENTO_MAXIMO = 400;

    @Override
    public String redactar(ContextoRespuesta contexto) {
        boolean ingles = contexto.idioma() == Idioma.en;
        StringBuilder texto = new StringBuilder();
        List<Sugerencia> sugerencias = contexto.sugerencias();
        List<EntradaConocimiento> conocimiento = contexto.conocimiento();
        if (!contexto.catalogoDisponible()) {
            texto.append(ingles
                    ? "I could not check the catalog right now, so I cannot suggest places or services. "
                    : "No pude consultar el catálogo en este momento, así que no puedo sugerirte lugares ni servicios. ");
        }
        if (!contexto.itinerario().isEmpty()) {
            int dias = contexto.itinerario().stream().mapToInt(Itinerario.Item::diaSugerido).max().orElse(1);
            texto.append(ingles
                    ? "Here is a suggested itinerary for " + dias + (dias == 1 ? " day" : " days")
                    + " with what I found in Samaria360. You can save it to your profile."
                    : "Te propongo un itinerario de " + dias + (dias == 1 ? " día" : " días")
                    + " con lo que encontré en Samaria360. Puedes guardarlo en tu perfil.");
        } else if (!sugerencias.isEmpty()) {
            texto.append(ingles ? "I found these options in Samaria360:" : "Encontré estas opciones en Samaria360:");
            for (Sugerencia s : sugerencias) {
                texto.append("\n• ").append(s.item().nombre()).append(": ").append(s.justificacion());
            }
        }
        if (!conocimiento.isEmpty()) {
            texto.append(texto.isEmpty() ? "" : "\n\n");
            texto.append(ingles ? "Useful information:" : "Información útil:");
            for (EntradaConocimiento entrada : conocimiento) {
                String contenido = entrada.contenido();
                if (contenido.length() > CONOCIMIENTO_MAXIMO) {
                    contenido = contenido.substring(0, CONOCIMIENTO_MAXIMO) + "…";
                }
                texto.append("\n• ").append(contenido);
            }
        }
        if (texto.isEmpty() || (sugerencias.isEmpty() && conocimiento.isEmpty())) {
            texto.append(texto.isEmpty() ? "" : "\n\n");
            texto.append(ingles
                    ? "I don't have information about that in Samaria360. Try asking about lodging, food, tours, "
                    + "equipment rental, beaches, nature or culture in Santa Marta."
                    : "No tengo información sobre eso en Samaria360. Pregúntame por alojamiento, gastronomía, tours, "
                    + "alquiler de equipos, playas, naturaleza o cultura en Santa Marta.");
        }
        return texto.toString().strip();
    }
}
