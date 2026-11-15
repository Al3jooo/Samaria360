package co.edu.unimagdalena.samaria360.auth.application.port.out;

import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoAdministrativo;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;

import java.util.UUID;

/**
 * Cuenta cuya contraseña se restableció (RF34): con su estado derivado, la versión ya incrementada y el RNT si es
 * prestador, para el evento CuentaActualizada que cierra las sesiones abiertas.
 */
public record CuentaRestablecida(UUID idUsuario, Rol rol, EstadoAdministrativo estado, long versionNueva, String rnt) {
}
