package co.edu.unimagdalena.samaria360.auth.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Token del enlace de recuperación de contraseña (RF34): vence a los 30 minutos y es de un solo uso. En
 * {@code token_recuperacion} solo se guarda su SHA-256 en hexadecimal, nunca el token en claro (CLAUDE.md).
 * El token son 32 bytes aleatorios en Base64 URL sin relleno (43 caracteres).
 */
public final class TokenRecuperacion {

    /** RF34: «expirará 30 minutos después de haber sido generado». */
    public static final Duration VIGENCIA = Duration.ofMinutes(30);

    private static final Pattern FORMA = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private TokenRecuperacion() {
    }

    public static Instant venceEn(Instant generadoEn) {
        return generadoEn.plus(VIGENCIA);
    }

    /** Un texto sin la forma de un token nunca coincide con uno guardado: se rechaza sin consultar la base. */
    public static boolean tieneForma(String token) {
        return token != null && FORMA.matcher(token).matches();
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
