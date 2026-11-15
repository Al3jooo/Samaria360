package co.edu.unimagdalena.samaria360.chatbot.infrastructure;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ms-catalog simulado (DEC-280): la búsqueda pública {@code GET /api/v1/catalogo/oferta} con un atractivo abierto,
 * uno cerrado y un tour; también el JWKS de ms-auth. Guarda las consultas para verificar que no llevan token.
 */
public final class CatalogoSimulado {

    public static final String PLAYA = "6a0000000000000000000001";
    public static final String TOUR = "6a0000000000000000000003";
    public static final List<String> CONSULTAS = new CopyOnWriteArrayList<>();
    public static volatile boolean caido;

    private static HttpServer servidor;

    private CatalogoSimulado() {
    }

    public static synchronized String url() {
        if (servidor == null) {
            try {
                servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            servidor.createContext("/jwks", intercambio -> responder(intercambio, 200, TokensDePrueba.jwks()));
            servidor.createContext("/api/v1/catalogo/oferta", intercambio -> {
                String autorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
                CONSULTAS.add(intercambio.getRequestURI().getRawQuery() + (autorizacion == null ? "" : "|token"));
                if (caido) {
                    responder(intercambio, 503, "{\"codigo\":\"SERVICIO_NO_DISPONIBLE\"}");
                    return;
                }
                responder(intercambio, 200, "{\"elementos\":["
                        + "{\"tipo\":\"Atractivo\",\"id\":\"" + PLAYA + "\",\"nombre\":\"Playa Cristal\","
                        + "\"categoria\":\"Naturaleza\",\"descripcionCorta\":\"Aguas claras\",\"precio\":null,"
                        + "\"estadoOperativo\":\"Abierto\"},"
                        + "{\"tipo\":\"Atractivo\",\"id\":\"6a0000000000000000000002\",\"nombre\":\"Pozo Azul\","
                        + "\"categoria\":\"Naturaleza\",\"estadoOperativo\":\"Cerrado_temporalmente\"},"
                        + "{\"tipo\":\"Servicio\",\"id\":\"" + TOUR + "\",\"nombre\":\"Tour Tayrona\","
                        + "\"categoria\":\"Tour\",\"precio\":120000}],"
                        + "\"pagina\":0,\"tamano\":10,\"totalElementos\":3,\"totalPaginas\":1}");
            });
            servidor.start();
        }
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    private static void responder(HttpExchange intercambio, int estado, String texto) throws IOException {
        byte[] cuerpo = texto.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, cuerpo.length);
        intercambio.getResponseBody().write(cuerpo);
        intercambio.close();
    }
}
