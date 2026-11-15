package co.edu.unimagdalena.samaria360.auth.application.fake;

import co.edu.unimagdalena.samaria360.auth.application.port.out.TokenEmitido;
import co.edu.unimagdalena.samaria360.auth.application.port.out.TokenProviderPort;
import co.edu.unimagdalena.samaria360.auth.domain.model.Usuario;

import java.time.Duration;
import java.time.Instant;

public class TokenProviderFalso implements TokenProviderPort {

    public static final Duration VIGENCIA = Duration.ofMinutes(15);

    private int llamadas;
    private Instant ultimoInicioSesion;

    @Override
    public TokenEmitido emitir(Usuario usuario, Instant ahora, Instant inicioSesion) {
        llamadas++;
        ultimoInicioSesion = inicioSesion;
        return new TokenEmitido("token-" + usuario.getId(), ahora.plus(VIGENCIA));
    }

    public Instant ultimoInicioSesion() {
        return ultimoInicioSesion;
    }

    public int llamadas() {
        return llamadas;
    }
}
