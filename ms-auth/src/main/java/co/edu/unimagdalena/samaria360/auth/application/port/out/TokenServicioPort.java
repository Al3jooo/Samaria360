package co.edu.unimagdalena.samaria360.auth.application.port.out;

import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;

import java.time.Instant;

/** JWT de servicio firmado por ms-auth (RS256, mismo JWKS), con {@code rol = SERVICIO} y 5 minutos de vigencia. */
public interface TokenServicioPort {

    /** Para el relevo HTTP (ADR-008): {@code sub = ms-auth}, {@code aud} = el destino. */
    String emitir(Destino destino, Instant ahora);

    /** Para un cliente interno (DEC-280): {@code sub} = el cliente, {@code aud = ms-auth}. */
    TokenEmitido emitirParaCliente(String cliente, Instant ahora);
}
