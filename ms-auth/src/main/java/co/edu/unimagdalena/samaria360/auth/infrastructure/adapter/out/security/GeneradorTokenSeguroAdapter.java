package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.security;

import co.edu.unimagdalena.samaria360.auth.application.port.out.GeneradorTokenPort;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** Token de recuperación (RF34): 32 bytes de {@link SecureRandom} (256 bits) en Base64 URL sin relleno. */
@Component
public class GeneradorTokenSeguroAdapter implements GeneradorTokenPort {

    private static final int BYTES = 32;

    private final SecureRandom aleatorio = new SecureRandom();

    @Override
    public String nuevoToken() {
        byte[] bytes = new byte[BYTES];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
