package co.edu.unimagdalena.samaria360.auth.infrastructure.config;

import co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.security.JwtServicioAdapter;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * {@code /internal/**} de ms-auth (DEC-280): solo un JWT de servicio ({@code rol = SERVICIO}) con {@code aud = ms-auth}.
 * Un token de usuario o el del relevo (con la {@code aud} de otro destino) no sirve.
 */
public class ValidadorTokenCliente implements OAuth2TokenValidator<Jwt> {

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        boolean deServicio = JwtServicioAdapter.ROL_SERVICIO.equals(jwt.getClaimAsString("rol"));
        boolean paraMsAuth = jwt.getAudience() != null && jwt.getAudience().contains(JwtServicioAdapter.AUDIENCIA_PROPIA);
        if (!deServicio || !paraMsAuth) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
                    "Solo un token de servicio para ms-auth", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
