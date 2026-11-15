package co.edu.unimagdalena.samaria360.analytics.infrastructure;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** El JWKS de ms-auth, servido con el {@code HttpServer} del JDK: ms-catalog lo descarga al validar el primer token. */
public final class JwksSimulado {

    private static HttpServer servidor;

    private JwksSimulado() {
    }

    /** Arranca el servidor la primera vez y devuelve la URI del JWKS; vive lo que la JVM de pruebas. */
    public static synchronized String uri() {
        if (servidor == null) {
            try {
                servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            servidor.createContext("/jwks", intercambio -> {
                byte[] cuerpo = TokensDePrueba.jwks().getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(200, cuerpo.length);
                intercambio.getResponseBody().write(cuerpo);
                intercambio.close();
            });
            servidor.setExecutor(null);
            servidor.start();
        }
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/jwks";
    }
}
