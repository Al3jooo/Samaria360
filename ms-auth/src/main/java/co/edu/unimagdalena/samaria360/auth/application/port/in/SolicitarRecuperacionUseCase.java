package co.edu.unimagdalena.samaria360.auth.application.port.in;

/**
 * RF34: envía el enlace de restablecimiento al correo registrado. La respuesta es la misma exista o no la cuenta
 * (ADR-006).
 */
public interface SolicitarRecuperacionUseCase {

    void solicitar(ComandoRecuperacion comando);
}
