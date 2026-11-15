package co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.out.modelo;

import co.edu.unimagdalena.samaria360.chatbot.application.port.out.ModeloLenguajePort.ContextoRespuesta;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.EntradaConocimiento;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Idioma;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.ItemCatalogo;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Sugerencia;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Los dos redactores (DEC-297): reglas en los dos idiomas y Gemini contra un servidor simulado, con respaldo. */
class RedactoresTest {

    private static final Sugerencia PLAYA = new Sugerencia(new ItemCatalogo("Atractivo", "6a0000000000000000000001",
            "Playa Cristal", "Naturaleza", "Aguas cristalinas", "Abierto", null), Sugerencia.Criterio.DISPONIBILIDAD,
            "Está abierto y disponible para visitar.");

    private HttpServer servidor;

    @AfterEach
    void detener() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    private static ContextoRespuesta contexto(Idioma idioma, List<Sugerencia> sugerencias,
                                              List<EntradaConocimiento> conocimiento) {
        return new ContextoRespuesta(idioma, "playas", sugerencias, conocimiento, List.of(), true);
    }

    @Test
    void lasReglasUsanSoloElContextoYDicenCuandoNoSaben() {
        RedactorReglasAdapter reglas = new RedactorReglasAdapter();

        String conDatos = reglas.redactar(contexto(Idioma.es, List.of(PLAYA), List.of()));
        String sinDatos = reglas.redactar(contexto(Idioma.en, List.of(), List.of()));
        String soloConocimiento = reglas.redactar(contexto(Idioma.es, List.of(),
                List.of(new EntradaConocimiento("k", "Lleva protector solar.", "Clima", Instant.now()))));

        assertThat(conDatos).contains("Playa Cristal").contains("disponible para visitar");
        assertThat(sinDatos).startsWith("I don't have information about that");
        assertThat(soloConocimiento).contains("Lleva protector solar.").doesNotContain("No tengo información");
    }

    @Test
    void geminiRecibeLaLlaveEnLaCabeceraYElContextoSinDatosPersonales() throws IOException {
        AtomicReference<String> llave = new AtomicReference<>();
        AtomicReference<String> cuerpo = new AtomicReference<>();
        AtomicReference<String> consulta = new AtomicReference<>();
        String url = simular(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Visita Playa Cristal.\"}]}}]}",
                llave, cuerpo, consulta, new AtomicInteger());
        RedactorGeminiAdapter gemini = new RedactorGeminiAdapter(url, "llave-secreta", "gemini-3.5-flash-lite", 400,
                new RedactorReglasAdapter());

        String texto = gemini.redactar(contexto(Idioma.en, List.of(PLAYA), List.of()));

        assertThat(texto).isEqualTo("Visita Playa Cristal.");
        assertThat(llave.get()).isEqualTo("llave-secreta");
        assertThat(consulta.get()).isNull();
        assertThat(cuerpo.get()).contains("Playa Cristal").contains("English").contains("\"maxOutputTokens\":400")
                .doesNotContain("llave-secreta");
    }

    @Test
    void aGeminiNoLeLlegaLaJustificacionQueDelataLosIntereses() {
        Sugerencia porIntereses = new Sugerencia(PLAYA.item(), Sugerencia.Criterio.PREFERENCIAS,
                "Coincide con tus intereses declarados (Naturaleza).");

        String prompt = RedactorGeminiAdapter.prompt(contexto(Idioma.es, List.of(porIntereses), List.of()));

        assertThat(prompt).contains("Playa Cristal").doesNotContain("intereses").doesNotContain("PREFERENCIAS")
                .doesNotContain("Justificación");
    }

    @Test
    void siGeminiFallaRedactaConLasReglas() throws IOException {
        AtomicInteger llamadas = new AtomicInteger();
        String url = simular(500, "{}", new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>(),
                llamadas);
        RedactorGeminiAdapter gemini = new RedactorGeminiAdapter(url, "x", "m", 400, new RedactorReglasAdapter());

        String texto = gemini.redactar(contexto(Idioma.es, List.of(PLAYA), List.of()));

        assertThat(llamadas.get()).isEqualTo(1);
        assertThat(texto).contains("Encontré estas opciones");
    }

    private String simular(int estado, String respuesta, AtomicReference<String> llave, AtomicReference<String> cuerpo,
                           AtomicReference<String> consulta, AtomicInteger llamadas) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", intercambio -> {
            llamadas.incrementAndGet();
            llave.set(intercambio.getRequestHeaders().getFirst("x-goog-api-key"));
            consulta.set(intercambio.getRequestURI().getQuery());
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    /** La pantalla del asistente muestra texto plano: las marcas de Markdown de Gemini no deben verse. */
    @org.junit.jupiter.api.Test
    void elTextoDeGeminiSeLimpiaDeMarkdown() {
        org.assertj.core.api.Assertions.assertThat(RedactorGeminiAdapter.textoPlano(
                        "## Opciones\nTe recomiendo la **Caminata a Pueblito Tayrona**.\n* Tour de café\n- Playa __Cristal__"))
                .isEqualTo("Opciones\nTe recomiendo la Caminata a Pueblito Tayrona.\n• Tour de café\n• Playa Cristal");
    }
}
