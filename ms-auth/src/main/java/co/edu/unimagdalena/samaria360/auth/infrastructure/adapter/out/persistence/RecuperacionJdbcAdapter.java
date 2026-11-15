package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.auth.application.port.out.CuentaRestablecida;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RecuperacionRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoAdministrativo;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoCuenta;
import co.edu.unimagdalena.samaria360.auth.domain.model.EstadoValidacion;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code token_recuperacion} (RF34, DEC-285): solo el hash SHA-256 del token, nunca el token en claro. Consumir es un
 * UPDATE condicional sobre {@code usado = false} y {@code expira_en > ahora}: de dos usos simultáneos del mismo enlace,
 * uno gana.
 */
@Component
public class RecuperacionJdbcAdapter implements RecuperacionRepositoryPort {

    private final JdbcTemplate jdbc;

    public RecuperacionJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> buscarIdPorCorreo(String correo) {
        List<UUID> ids = jdbc.queryForList("SELECT id_usuario FROM usuario WHERE correo = ?", UUID.class, correo);
        return ids.stream().findFirst();
    }

    @Override
    @Transactional
    public void guardarToken(UUID idUsuario, String tokenHash, Instant expiraEn) {
        jdbc.update("UPDATE token_recuperacion SET usado = true WHERE id_usuario = ? AND usado = false", idUsuario);
        jdbc.update("""
                INSERT INTO token_recuperacion (id_usuario, token_hash, expira_en, usado)
                VALUES (?, ?, ?, false)
                """, idUsuario, tokenHash, Timestamp.from(expiraEn));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> consumirToken(String tokenHash, Instant ahora) {
        List<UUID> usuario = jdbc.queryForList("""
                UPDATE token_recuperacion SET usado = true
                WHERE token_hash = ? AND usado = false AND expira_en > ?
                RETURNING id_usuario
                """, UUID.class, tokenHash, Timestamp.from(ahora));
        if (usuario.isEmpty()) {
            return Optional.empty();
        }
        jdbc.update("UPDATE token_recuperacion SET usado = true WHERE id_usuario = ? AND usado = false", usuario.get(0));
        return Optional.of(usuario.get(0));
    }

    /**
     * Un solo UPDATE: contraseña nueva, sin bloqueo de RF15 y {@code version_cuenta + 1} (cierra las sesiones abiertas,
     * ADR-008). Después lee el rol y el estado derivado (DEC-43) para el evento.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CuentaRestablecida restablecer(UUID idUsuario, String contrasenaHash) {
        Long version = jdbc.queryForObject("""
                UPDATE usuario
                SET contrasena_hash = ?, intentos_fallidos = 0, bloqueado_hasta = NULL,
                    version_cuenta = version_cuenta + 1
                WHERE id_usuario = ?
                RETURNING version_cuenta
                """, Long.class, contrasenaHash, idUsuario);
        return jdbc.queryForObject("""
                SELECT r.nombre AS rol, u.estado_cuenta::text AS estado_cuenta, p.estado_validacion, p.rnt
                FROM usuario u
                JOIN rol r ON r.id_rol = u.id_rol
                LEFT JOIN prestador p ON p.id_usuario = u.id_usuario
                WHERE u.id_usuario = ?
                """, (rs, n) -> {
            Rol rol = Rol.valueOf(rs.getString("rol"));
            EstadoCuenta cuenta = EstadoCuenta.valueOf(rs.getString("estado_cuenta"));
            EstadoAdministrativo estado = switch (rol) {
                case Prestador -> EstadoAdministrativo.dePrestador(
                        EstadoValidacion.desdeTexto(rs.getString("estado_validacion")), cuenta);
                case Turista -> EstadoAdministrativo.deTurista(cuenta);
                default -> EstadoAdministrativo.desdeTexto(cuenta.name());
            };
            return new CuentaRestablecida(idUsuario, rol, estado, version,
                    rol == Rol.Prestador ? rs.getString("rnt") : null);
        }, idUsuario);
    }
}
