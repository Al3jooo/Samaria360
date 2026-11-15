package co.edu.unimagdalena.samaria360.analytics.infrastructure;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ms-auth simulado para las pruebas (DEC-280): el JWKS, el token de servicio con las credenciales de cliente y los
 * contactos (solo el del prestador de prueba).
 */
public final class AuthSimulado {

    public static final String PRESTADOR = "00000000-0000-4000-8000-000000000002";
    public static final String CORREO_PRESTADOR = "prestador-rf30@samaria360.test";
    public static final String SECRETO = "secreto-de-prueba";
    public static final AtomicInteger PEDIDOS_DE_TOKEN = new AtomicInteger();

    private static HttpServer servidor;

    private AuthSimulado() {
    }

    public static synchronized String url() {
        if (servidor == null) {
            try {
                servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            servidor.createContext("/jwks", intercambio -> responder(intercambio, 200, TokensDePrueba.jwks()));
            servidor.createContext("/internal/v1/tokens", intercambio -> {
                String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                PEDIDOS_DE_TOKEN.incrementAndGet();
                if (!cuerpo.contains(SECRETO) || !cuerpo.contains("ms-analytics")) {
                    responder(intercambio, 401, "{\"codigo\":\"CREDENCIALES_SERVICIO_INVALIDAS\"}");
                    return;
                }
                responder(intercambio, 200, "{\"token\":\"token-de-servicio\",\"tipo\":\"Bearer\",\"expiraEn\":\""
                        + Instant.now().plusSeconds(300) + "\"}");
            });
            servidor.createContext("/internal/v1/contactos", intercambio -> {
                String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                boolean autorizado = "Bearer token-de-servicio"
                        .equals(intercambio.getRequestHeaders().getFirst("Authorization"));
                if (!autorizado) {
                    responder(intercambio, 401, "{\"codigo\":\"NO_AUTENTICADO\"}");
                    return;
                }
                String elementos = cuerpo.contains(PRESTADOR)
                        ? "[{\"idUsuario\":\"" + PRESTADOR + "\",\"rol\":\"Prestador\",\"nombre\":\"Luis Pérez\","
                        + "\"correo\":\"" + CORREO_PRESTADOR + "\",\"telefono\":\"3001234567\"}]"
                        : "[]";
                responder(intercambio, 200, "{\"elementos\":" + elementos + "}");
            });
            servidor.start();
        }
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    private static void responder(com.sun.net.httpserver.HttpExchange intercambio, int estado, String texto)
            throws IOException {
        byte[] cuerpo = texto.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, cuerpo.length);
        intercambio.getResponseBody().write(cuerpo);
        intercambio.close();
    }
}
