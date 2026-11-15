package co.edu.unimagdalena.samaria360.auth.infrastructure.config;

import co.edu.unimagdalena.samaria360.auth.application.port.in.ComandoRestablecimiento;
import co.edu.unimagdalena.samaria360.auth.application.port.in.RestablecerContrasenaUseCase;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RF34 en una transacción (DEC-285): consumir el token, cambiar la contraseña, subir la versión y encolar el evento van
 * juntos; si algo falla, el token sigue sin usar.
 */
public class RestablecerContrasenaTransaccional implements RestablecerContrasenaUseCase {

    private final RestablecerContrasenaUseCase delegado;
    private final TransactionTemplate transaccion;

    public RestablecerContrasenaTransaccional(RestablecerContrasenaUseCase delegado, TransactionTemplate transaccion) {
        this.delegado = delegado;
        this.transaccion = transaccion;
    }

    @Override
    public void restablecer(ComandoRestablecimiento comando) {
        transaccion.executeWithoutResult(estado -> delegado.restablecer(comando));
    }
}
