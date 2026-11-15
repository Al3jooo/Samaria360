package co.edu.unimagdalena.samaria360.auth.application.port.in;

/** RF34: cambia la contraseña con un token vigente y sin usar. */
public interface RestablecerContrasenaUseCase {

    void restablecer(ComandoRestablecimiento comando);
}
