package co.edu.unimagdalena.samaria360.chatbot.application.service;

import co.edu.unimagdalena.samaria360.chatbot.application.port.in.ProyectarCuentaUseCase;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.CuentaProyeccionPort;

import java.util.Objects;
import java.util.UUID;

/** Aplica {@code CuentaActualizada} a la proyección de chatbot_db (DEC-280): solo una versión mayor la cambia. */
public class ProyectarCuentaService implements ProyectarCuentaUseCase {

    private final CuentaProyeccionPort cuentas;

    public ProyectarCuentaService(CuentaProyeccionPort cuentas) {
        this.cuentas = Objects.requireNonNull(cuentas);
    }

    @Override
    public void proyectar(UUID idUsuario, String rol, String estadoCuenta, long version) {
        cuentas.actualizarSiMayor(Objects.requireNonNull(idUsuario), Objects.requireNonNull(rol),
                Objects.requireNonNull(estadoCuenta), version);
    }
}
