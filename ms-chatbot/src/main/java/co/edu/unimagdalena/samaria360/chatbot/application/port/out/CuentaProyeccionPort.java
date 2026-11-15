package co.edu.unimagdalena.samaria360.chatbot.application.port.out;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Proyección técnica {@code cuentas}: solo avanza si la versión es mayor. */
public interface CuentaProyeccionPort {

    void actualizarSiMayor(UUID idUsuario, String rol, String estadoCuenta, long version);

    OptionalLong versionDe(UUID idUsuario);
}
