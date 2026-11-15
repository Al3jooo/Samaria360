package co.edu.unimagdalena.samaria360.chatbot.application.port.in;

import java.util.UUID;

/**
 * Proyección de cuentas de chatbot_db con {@code CuentaActualizada} de RabbitMQ (DEC-280): el validador de {@code ver}
 * la necesita para el Administrador (RF05, DEC-110). Solo avanza si la versión es mayor: idempotente.
 */
public interface ProyectarCuentaUseCase {

    void proyectar(UUID idUsuario, String rol, String estadoCuenta, long version);
}
