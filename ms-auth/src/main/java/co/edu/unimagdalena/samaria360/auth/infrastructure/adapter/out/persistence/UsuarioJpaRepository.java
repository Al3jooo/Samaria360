package co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.auth.infrastructure.adapter.out.persistence.entity.UsuarioEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioJpaRepository extends JpaRepository<UsuarioEntity, UUID> {

    /**
     * Bloquea la fila del usuario (SELECT ... FOR UPDATE) para que dos intentos simultáneos no pierdan un
     * incremento de intentos_fallidos. Sin join fetch: el bloqueo no debe alcanzar la fila de rol, que comparten
     * todos los usuarios del mismo rol.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UsuarioEntity u where u.correo = :correo")
    Optional<UsuarioEntity> buscarPorCorreoParaActualizar(@Param("correo") String correo);

    /**
     * Lectura de turista y prestador sin mapearlos como entidades. NULL para Gestor y Administrador,
     * que no tienen nombre en el modelo lógico.
     */
    @Query(value = """
            SELECT COALESCE(t.primer_nombre || ' ' || t.primer_apellido, p.nombre_representante_legal)
            FROM usuario u
            LEFT JOIN turista t ON t.id_usuario = u.id_usuario
            LEFT JOIN prestador p ON p.id_usuario = u.id_usuario
            WHERE u.id_usuario = :id
            """, nativeQuery = true)
    Optional<String> buscarNombreVisible(@Param("id") UUID id);

    /** Sin bloqueo: solo para saber si el correo ya tiene cuenta (registro). */
    @Query("select u.idUsuario from UsuarioEntity u where u.correo = :correo")
    Optional<UUID> buscarIdPorCorreo(@Param("correo") String correo);

    /** Vacío si el usuario no es prestador. */
    @Query(value = "SELECT estado_validacion FROM prestador WHERE id_usuario = :id", nativeQuery = true)
    Optional<String> buscarEstadoValidacion(@Param("id") UUID id);

    /** Vacío si el usuario no es prestador o no fue rechazado (DEC-273). */
    @Query(value = "SELECT motivo_rechazo FROM prestador WHERE id_usuario = :id", nativeQuery = true)
    Optional<String> buscarMotivoRechazo(@Param("id") UUID id);

    /** Versión de la cuenta, leída de la base en cada llamada (el claim {@code ver} se compara con ella). */
    @Query(value = "SELECT version_cuenta FROM usuario WHERE id_usuario = :id", nativeQuery = true)
    Optional<Long> buscarVersionCuenta(@Param("id") UUID id);
}
