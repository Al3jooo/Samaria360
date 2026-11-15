package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.security;

import co.edu.unimagdalena.samaria360.auth.application.port.out.TokenEmitido;
import co.edu.unimagdalena.samaria360.auth.application.port.out.TokenProviderPort;
import co.edu.unimagdalena.samaria360.auth.domain.model.Usuario;
import co.edu.unimagdalena.samaria360.auth.infrastructure.config.JwtPropiedades;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** JWT RS256 con claims iss, sub (id_usuario), rol, ver, ini, iat y exp; el kid coincide con el del JWKS. */
@Component
public class JwtTokenProviderAdapter implements TokenProviderPort {

    public static final String CLAIM_ROL = "rol";
    /** Versión de la cuenta al iniciar sesión (DEC-29): los servicios rechazan el token si ya hubo un cambio. */
    public static final String CLAIM_VERSION = "ver";
    /** Inicio de la sesión, en segundos desde la época (DEC-317): se conserva al renovar el token. */
    public static final String CLAIM_INICIO_SESION = "ini";

    private final JwtEncoder encoder;
    private final JwtPropiedades propiedades;
    private final String kid;

    public JwtTokenProviderAdapter(JwtEncoder encoder, JwtPropiedades propiedades, RSAKey llaveJwt) {
        this.encoder = encoder;
        this.propiedades = propiedades;
        this.kid = llaveJwt.getKeyID();
    }

    @Override
    public TokenEmitido emitir(Usuario usuario, Instant ahora, Instant inicioSesion) {
        // iat y exp del JWT son segundos enteros (NumericDate, RFC 7519): se trunca para que
        // expiraEn de la respuesta coincida exactamente con el exp del token.
        Instant emitidoEn = ahora.truncatedTo(ChronoUnit.SECONDS);
        Instant expiraEn = emitidoEn.plus(propiedades.expiracion());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(propiedades.emisor())
                .subject(usuario.getId().toString())
                .claim(CLAIM_ROL, usuario.getRol().name())
                .claim(CLAIM_VERSION, usuario.getVersionCuenta())
                .claim(CLAIM_INICIO_SESION, inicioSesion.getEpochSecond())
                .issuedAt(emitidoEn)
                .expiresAt(expiraEn)
                .build();
        JwsHeader cabecera = JwsHeader.with(SignatureAlgorithm.RS256).keyId(kid).build();
        String token = encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new TokenEmitido(token, expiraEn);
    }
}
