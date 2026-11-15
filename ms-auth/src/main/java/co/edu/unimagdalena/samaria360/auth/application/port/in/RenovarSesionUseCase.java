package co.edu.unimagdalena.samaria360.auth.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** Renueva el token de una sesión vigente (DEC-317): el cliente lo pide antes de que venza, si el usuario sigue activo. */
public interface RenovarSesionUseCase {

    /**
     * @param inicioSesion cuándo empezó la sesión (claim {@code ini}, o {@code iat} en los tokens anteriores)
     * @throws co.edu.unimagdalena.samaria360.auth.domain.exception.SesionVencidaException si pasó la duración máxima
     * @throws co.edu.unimagdalena.samaria360.auth.domain.exception.CuentaInactivaException si la cuenta ya no está activa
     */
    SesionIniciada renovar(UUID idUsuario, Instant inicioSesion);
}
