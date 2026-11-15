package co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.out.modelo;

import co.edu.unimagdalena.samaria360.chatbot.application.port.out.ModeloLenguajePort;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.EntradaConocimiento;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Idioma;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Itinerario;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Sugerencia;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Redactor con Gemini, el «Proveedor LLM» de la figura 20 (decisión 5 del equipo; DEC-297). La llave va en la cabecera
 * {@code x-goog-api-key} (nunca en la URL ni en los logs). Se envía solo el mensaje, las sugerencias (sin su
 * justificación, que delataría los intereses del turista) y la base de conocimientos: ningún dato personal (el nivel gratuito puede usar lo enviado). La instrucción del sistema restringe el
 * modelo a ese contexto, en el idioma de la interfaz (RF59), y le pide decir que no sabe si no está ahí (RF05). Tope de
 * tokens por respuesta. Si Gemini falla o tarda, redacta con las reglas.
 */
public class RedactorGeminiAdapter implements ModeloLenguajePort {

    private static final Logger LOG = LoggerFactory.getLogger(RedactorGeminiAdapter.class);

    private final RestClient cliente;
    private final String llave;
    private final String modelo;
    private final int maximoTokens;
    private final ModeloLenguajePort respaldo;

    public RedactorGeminiAdapter(String url, String llave, String modelo, int maximoTokens, ModeloLenguajePort respaldo) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(2));
        fabrica.setReadTimeout(Duration.ofSeconds(8));
        this.cliente = RestClient.builder().baseUrl(url).requestFactory(fabrica).build();
        this.llave = llave;
        this.modelo = modelo;
        this.maximoTokens = maximoTokens;
        this.respaldo = respaldo;
    }

    @Override
    public String redactar(ContextoRespuesta contexto) {
        try {
            JsonNode respuesta = cliente.post().uri("/v1beta/models/{modelo}:generateContent", modelo)
                    .header("x-goog-api-key", llave)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "systemInstruction", Map.of("parts", List.of(Map.of("text", instruccion(contexto.idioma())))),
                            "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt(contexto))))),
                            "generationConfig", Map.of("maxOutputTokens", maximoTokens, "temperature", 0.3)))
                    .retrieve().body(JsonNode.class);
            String texto = respuesta == null ? "" : respuesta.at("/candidates/0/content/parts/0/text").asText("");
            if (texto.isBlank()) {
                return respaldo.redactar(contexto);
            }
            return textoPlano(texto);
        } catch (RestClientException error) {
            LOG.warn("Gemini no respondió ({}); se redacta con las reglas", error.getClass().getSimpleName());
            return respaldo.redactar(contexto);
        }
    }

    static String instruccion(Idioma idioma) {
        String lengua = idioma == Idioma.en ? "English" : "español";
        return "Eres el asistente turístico de Samaria360 (Santa Marta, Colombia). Responde SIEMPRE en " + lengua
                + ", en menos de 120 palabras. Usa SOLO la información de la sección CONTEXTO: las opciones del catálogo"
                + " y la base de conocimientos. No inventes lugares, precios, horarios ni datos que no estén ahí. Si el"
                + " contexto no responde la pregunta, di claramente que no tienes esa información. No pidas ni repitas"
                + " datos personales. Escribe en texto plano, sin Markdown (sin asteriscos, almohadillas ni guiones"
                + " bajos de formato).";
    }

    /** La pantalla muestra texto plano: si el modelo igual usa Markdown, se quitan las marcas de énfasis y títulos. */
    static String textoPlano(String texto) {
        return texto.replace("**", "").replace("__", "")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[*-]\\s+", "• ")
                .strip();
    }

    static String prompt(ContextoRespuesta contexto) {
        StringBuilder texto = new StringBuilder("MENSAJE DEL TURISTA:\n").append(contexto.mensaje()).append("\n\nCONTEXTO\n");
        if (!contexto.catalogoDisponible()) {
            texto.append("- El catálogo no está disponible en este momento.\n");
        }
        // Sin la justificación de RF36 ni el criterio: «Coincide con tus intereses (Naturaleza)» revelaría los
        // intereses del turista (DEC-297). La pantalla muestra la justificación de cada sugerencia aparte.
        texto.append("Opciones del catálogo:\n");
        for (Sugerencia s : contexto.sugerencias()) {
            texto.append("- ").append(s.item().nombre()).append(" (").append(s.item().categoria()).append("): ")
                    .append(s.item().descripcionCorta()).append('\n');
        }
        if (!contexto.itinerario().isEmpty()) {
            texto.append("Itinerario propuesto:\n");
            for (Itinerario.Item item : contexto.itinerario()) {
                texto.append("- Día ").append(item.diaSugerido()).append(": ").append(item.tituloActividad()).append('\n');
            }
        }
        texto.append("Base de conocimientos:\n");
        for (EntradaConocimiento entrada : contexto.conocimiento()) {
            texto.append("- [").append(entrada.categoria()).append("] ").append(entrada.contenido()).append('\n');
        }
        return texto.toString();
    }
}
