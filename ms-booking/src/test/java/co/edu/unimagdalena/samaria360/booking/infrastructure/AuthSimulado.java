package co.edu.unimagdalena.samaria360.booking.infrastructure;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ms-auth interno simulado para RF42 (DEC-280, DEC-312): el token de servicio con las credenciales de ms-booking y los
 * contactos de los turistas registrados con {@link #registrar}.
 */
public final class AuthSimulado {

    public static final String SECRETO = "secreto-booking-de-prueba";
    public static final AtomicBoolean CAIDO = new AtomicBoolean();
    private static final Map<UUID, String> CONTACTOS = new ConcurrentHashMap<>();
    private static HttpServer servidor;

    private AuthSimulado() {
    }

    public static void registrar(UUID turista, String nombre, String correo) {
        CONTACTOS.put(turista, "{\"idUsuario\":\"" + turista + "\",\"rol\":\"Turista\",\"nombre\":\"" + nombre
                + "\",\"correo\":\"" + correo + "\",\"telefono\":null}");
    }

    public static synchronized String url() {
        if (servidor == null) {
            try {
                servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            servidor.createContext("/internal/v1/tokens", intercambio -> {
                String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (CAIDO.get()) {
                    responder(intercambio, 503, "{}");
                } else if (!cuerpo.contains(SECRETO) || !cuerpo.contains("ms-booking")) {
                    responder(intercambio, 401, "{\"codigo\":\"CREDENCIALES_SERVICIO_INVALIDAS\"}");
                } else {
                    responder(intercambio, 200, "{\"token\":\"token-booking\",\"tipo\":\"Bearer\",\"expiraEn\":\""
                            + Instant.now().plusSeconds(300) + "\"}");
                }
            });
            servidor.createContext("/internal/v1/contactos", intercambio -> {
                String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (CAIDO.get() || !"Bearer token-booking".equals(intercambio.getRequestHeaders().getFirst("Authorization"))) {
                    responder(intercambio, CAIDO.get() ? 503 : 401, "{}");
                    return;
                }
                StringBuilder elementos = new StringBuilder();
                CONTACTOS.forEach((id, json) -> {
                    if (cuerpo.contains(id.toString())) {
                        elementos.append(elementos.isEmpty() ? "" : ",").append(json);
                    }
                });
                responder(intercambio, 200, "{\"elementos\":[" + elementos + "]}");
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
