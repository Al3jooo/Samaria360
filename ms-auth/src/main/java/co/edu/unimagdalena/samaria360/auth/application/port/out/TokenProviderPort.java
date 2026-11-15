package co.edu.unimagdalena.samaria360.auth.application.port.out;

import co.edu.unimagdalena.samaria360.auth.domain.model.Usuario;

import java.time.Instant;

/** Emisión del token de sesión (JWT RS256 con claims {@code sub}, {@code rol}, {@code ver}, {@code ini} y {@code exp}). */
public interface TokenProviderPort {

    /** Token de un inicio de sesión: la sesión empieza ahora. */
    default TokenEmitido emitir(Usuario usuario, Instant ahora) {
        return emitir(usuario, ahora, ahora);
    }

    /**
     * Token con el inicio de la sesión ({@code ini}) que se conserva al renovarla (DEC-317), para que la sesión no dure
     * más que {@code RenovarSesionService.DURACION_MAXIMA} desde el inicio de sesión.
     */
    TokenEmitido emitir(Usuario usuario, Instant ahora, Instant inicioSesion);
}
