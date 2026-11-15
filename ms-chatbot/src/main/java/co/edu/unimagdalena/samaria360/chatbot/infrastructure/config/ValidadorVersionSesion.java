package co.edu.unimagdalena.samaria360.chatbot.infrastructure.config;

import co.edu.unimagdalena.samaria360.chatbot.application.port.in.VerificarSesionUseCase;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Después de la firma, {@code iss} y {@code exp}: el claim {@code ver} contra la versión de la proyección {@code cuentas}
 * (DEC-29, ADR-008). Sin fila para la cuenta se acepta, salvo que el rol del token sea Gestor o Administrador (DEC-110);
 * un token sin {@code ver} cuenta como 0. También rechaza lo que no es un token de usuario: uno de servicio
 * ({@code rol = SERVICIO}) o uno cuyo {@code sub} no es un UUID.
 */
public class ValidadorVersionSesion implements OAuth2TokenValidator<Jwt> {

    /** Código del error cuando el token es de antes de un cambio de estado o de rol (401 SESION_INVALIDADA). */
    public static final String SESION_INVALIDADA = "sesion_invalidada";
    static final String ROL_SERVICIO = "SERVICIO";

    private final VerificarSesionUseCase sesiones;

    public ValidadorVersionSesion(VerificarSesionUseCase sesiones) {
        this.sesiones = sesiones;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String rol = jwt.getClaimAsString("rol");
        if (ROL_SERVICIO.equals(rol)) {
            return fallo(OAuth2ErrorCodes.INVALID_TOKEN, "Un token de servicio no sirve para la API pública");
        }
        UUID idUsuario;
        try {
            idUsuario = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallo(OAuth2ErrorCodes.INVALID_TOKEN, "El sujeto del token no es un usuario");
        }
        Object ver = jwt.getClaim("ver");
        long version = ver instanceof Number numero ? numero.longValue() : 0;
        if (!sesiones.sesionVigente(idUsuario, version, rol)) {
            return fallo(SESION_INVALIDADA, "La cuenta cambió después de iniciar sesión");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult fallo(String codigo, String descripcion) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(codigo, descripcion, null));
    }
}
