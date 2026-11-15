package co.edu.unimagdalena.samaria360.chatbot.application.port.in;

import java.util.UUID;

/** Validador del claim {@code ver} (ADR-008, DEC-110), con la proyección de cuentas de ms-chatbot. */
public interface VerificarSesionUseCase {

    boolean sesionVigente(UUID idUsuario, long ver, String rol);
}
