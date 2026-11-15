package co.edu.unimagdalena.samaria360.analytics.application.port.in;

import java.util.UUID;

/** Validador del claim {@code ver} (ADR-008, DEC-110), con la proyección de cuentas de ms-analytics. */
public interface VerificarSesionUseCase {

    boolean sesionVigente(UUID idUsuario, long ver, String rol);
}
