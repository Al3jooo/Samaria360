package co.edu.unimagdalena.samaria360.auth.domain.exception;

/**
 * El token de recuperación no existe, ya se usó o venció (RF34). Una sola respuesta para los tres casos: no se revela
 * cuál fue.
 */
public class TokenRecuperacionInvalidoException extends RuntimeException {

    public TokenRecuperacionInvalidoException() {
        super("Token de recuperación inválido, usado o vencido");
    }
}
