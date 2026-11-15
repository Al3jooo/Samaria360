package co.edu.unimagdalena.samaria360.auth.application.port.out;

import co.edu.unimagdalena.samaria360.auth.domain.model.Idioma;

import java.util.UUID;

/**
 * Correos al dueño de una cuenta, enviados por ms-auth por SMTP directo (DEC-278, DEC-282). El envío no bloquea la
 * respuesta y nunca escribe la dirección en los logs.
 */
public interface NotificadorCuentaPort {

    /** «Olvidé que tenía cuenta»: alguien intentó registrarse con su correo (ADR-006). */
    void avisarRegistroConCorreoExistente(UUID idUsuario, String correo);

    /** Enlace de recuperación de contraseña (RF34), con el token en claro: solo viaja en el correo. */
    void enviarRecuperacion(UUID idUsuario, String correo, String token, Idioma idioma);
}
