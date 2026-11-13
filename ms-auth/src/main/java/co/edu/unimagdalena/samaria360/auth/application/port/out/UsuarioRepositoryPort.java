package co.edu.unimagdalena.samaria360.auth.application.port.out;

import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoValidacion;
import co.edu.unimagdalena.samaria360.auth.domain.model.Usuario;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public interface UsuarioRepositoryPort {

    /** @param correo ya normalizado (sin espacios y en minúsculas) */
    Optional<Usuario> buscarPorCorreo(String correo);

    Optional<Usuario> buscarPorId(UUID id);

    /**
     * Nombre para mostrar según el subtipo: Turista → primer nombre y primer apellido;
     * Prestador → representante legal. Vacío si el subtipo no tiene nombre (Gestor, Administrador).
     */
    Optional<String> buscarNombreVisible(UUID id);

    /** Estado de validación si el usuario es prestador (RF02, RF28); vacío para los demás roles. */
    Optional<EstadoValidacion> buscarEstadoValidacion(UUID id);

    /** Justificación del rechazo del prestador ({@code prestador.motivo_rechazo}, RF28); vacío si no hay (DEC-273). */
    Optional<String> buscarMotivoRechazo(UUID id);

    /** {@code usuario.version_cuenta}; vacío si el usuario no existe (DEC-29). */
    OptionalLong buscarVersionCuenta(UUID id);

    /** Persiste {@code intentos_fallidos} y {@code bloqueado_hasta}. */
    void guardar(Usuario usuario);
}
