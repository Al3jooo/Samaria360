package co.edu.unimagdalena.samaria360.auth.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Tokens de recuperación de contraseña ({@code token_recuperacion}, RF34) y el restablecimiento. */
public interface RecuperacionRepositoryPort {

    /** @param correo ya normalizado (sin espacios y en minúsculas) */
    Optional<UUID> buscarIdPorCorreo(String correo);

    /** Guarda el hash de un token nuevo y anula los anteriores sin usar del mismo usuario (solo vale el último). */
    void guardarToken(UUID idUsuario, String tokenHash, Instant expiraEn);

    /**
     * Marca usado el token si existe, no se usó y no venció (UPDATE condicional: de dos usos simultáneos gana uno) y
     * anula los demás del usuario.
     *
     * @return el usuario del token; vacío si no existe, ya se usó o venció
     */
    Optional<UUID> consumirToken(String tokenHash, Instant ahora);

    /**
     * Cambia la contraseña, quita el bloqueo de RF15 (intentos en 0, sin {@code bloqueado_hasta}) y sube
     * {@code version_cuenta} en 1, en la transacción de quien llama.
     */
    CuentaRestablecida restablecer(UUID idUsuario, String contrasenaHash);
}
