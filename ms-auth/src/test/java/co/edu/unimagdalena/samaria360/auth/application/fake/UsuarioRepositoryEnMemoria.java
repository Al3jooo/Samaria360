package co.edu.unimagdalena.samaria360.auth.application.fake;

import co.edu.unimagdalena.samaria360.auth.application.port.out.UsuarioRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoValidacion;
import co.edu.unimagdalena.samaria360.auth.domain.model.Usuario;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Guarda copias, como una base de datos real: si el caso de uso modifica el usuario
 * y no llama a {@link #guardar}, el cambio no queda persistido.
 */
public class UsuarioRepositoryEnMemoria implements UsuarioRepositoryPort {

    private final Map<UUID, Usuario> usuarios = new HashMap<>();
    private final Map<UUID, String> nombresVisibles = new HashMap<>();
    private final Map<UUID, EstadoValidacion> validaciones = new HashMap<>();
    private int llamadasGuardar;

    /** Precarga un usuario sin contar como llamada a {@link #guardar}. */
    public void agregar(Usuario usuario) {
        usuarios.put(usuario.getId(), copiar(usuario));
    }

    /** Precarga un usuario con el nombre de su subtipo (Turista o Prestador). */
    public void agregar(Usuario usuario, String nombreVisible) {
        agregar(usuario);
        nombresVisibles.put(usuario.getId(), nombreVisible);
    }

    /** Precarga un prestador con su estado de validación (RF02, RF28). */
    public void agregarPrestador(Usuario usuario, EstadoValidacion validacion) {
        agregar(usuario);
        validaciones.put(usuario.getId(), validacion);
    }

    @Override
    public Optional<EstadoValidacion> buscarEstadoValidacion(UUID id) {
        return Optional.ofNullable(validaciones.get(id));
    }

    private final java.util.Map<UUID, String> motivos = new java.util.HashMap<>();

    /** Precarga la justificación del rechazo de un prestador (DEC-273). */
    public void motivoRechazo(UUID id, String motivo) {
        motivos.put(id, motivo);
    }

    @Override
    public Optional<String> buscarMotivoRechazo(UUID id) {
        return Optional.ofNullable(motivos.get(id));
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) {
        return usuarios.values().stream()
                .filter(u -> u.getCorreo().equals(correo))
                .findFirst()
                .map(UsuarioRepositoryEnMemoria::copiar);
    }

    @Override
    public Optional<Usuario> buscarPorId(UUID id) {
        return Optional.ofNullable(usuarios.get(id)).map(UsuarioRepositoryEnMemoria::copiar);
    }

    @Override
    public Optional<String> buscarNombreVisible(UUID id) {
        return Optional.ofNullable(nombresVisibles.get(id));
    }

    @Override
    public OptionalLong buscarVersionCuenta(UUID id) {
        Usuario usuario = usuarios.get(id);
        return usuario == null ? OptionalLong.empty() : OptionalLong.of(usuario.getVersionCuenta());
    }

    @Override
    public void guardar(Usuario usuario) {
        llamadasGuardar++;
        usuarios.put(usuario.getId(), copiar(usuario));
    }

    /** Estado persistido del usuario. */
    public Usuario persistido(UUID id) {
        return buscarPorId(id).orElseThrow();
    }

    public int llamadasGuardar() {
        return llamadasGuardar;
    }

    private static Usuario copiar(Usuario u) {
        return new Usuario(u.getId(), u.getCorreo(), u.getContrasenaHash(), u.getRol(), u.getEstadoCuenta(),
                u.getIntentosFallidos(), u.getBloqueadoHasta().orElse(null), u.getVersionCuenta());
    }
}
