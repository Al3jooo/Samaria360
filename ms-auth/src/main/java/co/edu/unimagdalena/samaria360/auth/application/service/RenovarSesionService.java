package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.port.in.RenovarSesionUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.in.SesionIniciada;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.TokenEmitido;
import co.edu.unimagdalena.samaria360.auth.application.port.out.TokenProviderPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.UsuarioRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaInactivaException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.SesionVencidaException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.UsuarioNoEncontradoException;
import co.edu.unimagdalena.samaria360.auth.domain.model.Usuario;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Renovación de la sesión (DEC-317, en lugar de un token de refresco). El token vigente (firma, {@code exp} y
 * {@code ver}) ya lo validó la cadena de seguridad; aquí se exige que la cuenta siga Activa y que la sesión no supere
 * {@link #DURACION_MAXIMA} desde el inicio de sesión. El token nuevo conserva el inicio ({@code ini}) y lleva la versión
 * actual de la cuenta. No escribe en la base: no hay tokens guardados que revocar.
 */
public class RenovarSesionService implements RenovarSesionUseCase {

    /** Una jornada de trabajo: después hay que volver a escribir la contraseña. */
    public static final Duration DURACION_MAXIMA = Duration.ofHours(8);

    private final UsuarioRepositoryPort usuarios;
    private final TokenProviderPort tokens;
    private final RelojPort reloj;

    public RenovarSesionService(UsuarioRepositoryPort usuarios, TokenProviderPort tokens, RelojPort reloj) {
        this.usuarios = Objects.requireNonNull(usuarios);
        this.tokens = Objects.requireNonNull(tokens);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public SesionIniciada renovar(UUID idUsuario, Instant inicioSesion) {
        Instant ahora = reloj.ahora();
        if (inicioSesion == null || inicioSesion.plus(DURACION_MAXIMA).isBefore(ahora)) {
            throw new SesionVencidaException();
        }
        Usuario usuario = usuarios.buscarPorId(idUsuario)
                .orElseThrow(() -> new UsuarioNoEncontradoException(idUsuario));
        if (!usuario.getEstadoCuenta().permiteIniciarSesion()) {
            throw new CuentaInactivaException(usuario.getEstadoCuenta());
        }
        TokenEmitido token = tokens.emitir(usuario, ahora, inicioSesion);
        return new SesionIniciada(token.token(), token.expiraEn(), usuario.getId(), usuario.getRol());
    }
}
