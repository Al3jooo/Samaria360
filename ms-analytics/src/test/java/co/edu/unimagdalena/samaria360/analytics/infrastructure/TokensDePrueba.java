package co.edu.unimagdalena.samaria360.analytics.infrastructure;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT RS256 como los de ms-auth, firmados con una llave generada para las pruebas; su JWKS lo publica
 * {@link JwksSimulado}. Nimbus ya llega con spring-boot-starter-oauth2-resource-server: no hay dependencias nuevas.
 */
public final class TokensDePrueba {

    private static final RSAKey LLAVE = generar();

    private TokensDePrueba() {
    }

    public static String jwks() {
        return new JWKSet(LLAVE.toPublicJWK()).toString();
    }

    /** Token de usuario con el claim {@code ver}. */
    public static String usuario(UUID id, String rol, long ver) {
        return firmar(new JWTClaimsSet.Builder().subject(id.toString()).claim("rol", rol).claim("ver", ver));
    }

    /** Token de usuario sin el claim {@code ver} (anterior a esta iteración): cuenta como 0. */
    public static String usuarioSinVer(UUID id, String rol) {
        return firmar(new JWTClaimsSet.Builder().subject(id.toString()).claim("rol", rol));
    }

    /** JWT de servicio como el del relevo de ms-auth. */
    public static String servicio(String audiencia) {
        return firmar(new JWTClaimsSet.Builder().subject("ms-auth").claim("rol", "SERVICIO")
                .audience(List.of(audiencia)));
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String firmar(JWTClaimsSet.Builder datos) {
        JWTClaimsSet claims = datos.issuer("samaria360-auth").issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(LLAVE.getKeyID()).build(), claims);
        try {
            jwt.sign(new RSASSASigner(LLAVE));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    private static RSAKey generar() {
        try {
            return new RSAKeyGenerator(2048).keyID("prueba").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
