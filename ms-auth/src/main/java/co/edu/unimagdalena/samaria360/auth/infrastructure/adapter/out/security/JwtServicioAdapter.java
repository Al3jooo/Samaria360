package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.security;

import co.edu.unimagdalena.samaria360.auth.application.port.out.TokenEmitido;
import co.edu.unimagdalena.samaria360.auth.application.port.out.TokenServicioPort;
import co.edu.unimagdalena.samaria360.auth.domain.model.Destino;
import co.edu.unimagdalena.samaria360.auth.infrastructure.config.JwtPropiedades;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * JWT de servicio del relevo (ADR-008): firmado con la misma llave y publicado en el mismo JWKS que los de usuario, con
 * {@code sub = ms-auth}, {@code rol = SERVICIO}, {@code aud} = el destino y {@code exp} de 5 minutos. Cada destino
 * rechaza uno con otra {@code aud}; la API pública rechaza cualquier token con {@code rol = SERVICIO}.
 */
@Component
public class JwtServicioAdapter implements TokenServicioPort {

    public static final String ROL_SERVICIO = "SERVICIO";
    public static final String SUJETO = "ms-auth";
    /** {@code aud} de los tokens de los clientes internos: solo los acepta el /internal/** de ms-auth (DEC-280). */
    public static final String AUDIENCIA_PROPIA = "ms-auth";
    static final Duration VIGENCIA = Duration.ofMinutes(5);

    private final JwtEncoder encoder;
    private final JwtPropiedades propiedades;
    private final String kid;

    public JwtServicioAdapter(JwtEncoder encoder, JwtPropiedades propiedades, RSAKey llaveJwt) {
        this.encoder = encoder;
        this.propiedades = propiedades;
        this.kid = llaveJwt.getKeyID();
    }

    @Override
    public String emitir(Destino destino, Instant ahora) {
        return firmar(SUJETO, destino.texto(), ahora).token();
    }

    /** DEC-280: el cliente interno es el sujeto; la audiencia es ms-auth (solo sirve en su /internal/**). */
    @Override
    public TokenEmitido emitirParaCliente(String cliente, Instant ahora) {
        return firmar(cliente, AUDIENCIA_PROPIA, ahora);
    }

    private TokenEmitido firmar(String sujeto, String audiencia, Instant ahora) {
        Instant emitidoEn = ahora.truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(propiedades.emisor())
                .subject(sujeto)
                .audience(List.of(audiencia))
                .claim(JwtTokenProviderAdapter.CLAIM_ROL, ROL_SERVICIO)
                .issuedAt(emitidoEn)
                .expiresAt(emitidoEn.plus(VIGENCIA))
                .build();
        JwsHeader cabecera = JwsHeader.with(SignatureAlgorithm.RS256).keyId(kid).build();
        String token = encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new TokenEmitido(token, emitidoEn.plus(VIGENCIA));
    }
}
