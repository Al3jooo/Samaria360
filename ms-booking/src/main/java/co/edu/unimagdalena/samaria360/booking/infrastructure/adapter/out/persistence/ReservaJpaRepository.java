package co.edu.unimagdalena.samaria360.booking.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.booking.domain.model.EstadoReserva;
import co.edu.unimagdalena.samaria360.booking.infrastructure.adapter.out.persistence.entity.ReservaEntity;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Solo consultas: extiende {@link Repository} y no {@code JpaRepository} a propósito, para que no exista
 * {@code save()} (el id lo asigna el dominio; insertar es {@code EntityManager.persist}).
 * {@link JpaSpecificationExecutor} agrega las consultas con filtros de «Mis reservas» y tampoco tiene {@code save()}.
 */
interface ReservaJpaRepository extends Repository<ReservaEntity, UUID>, JpaSpecificationExecutor<ReservaEntity> {

    Optional<ReservaEntity> findByIdTuristaAndClaveIdempotencia(UUID idTurista, UUID claveIdempotencia);

    Optional<ReservaEntity> findByIdReservaAndIdTurista(UUID idReserva, UUID idTurista);


    /**
     * UPDATE condicional (D3): solo si sigue Confirmada. Limpia el contexto de persistencia para que la lectura
     * siguiente vea el estado real y no una entidad en caché.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ReservaEntity r set r.estado = :cancelada, r.fechaCancelacion = :ahora"
            + " where r.idReserva = :idReserva and r.idTurista = :idTurista and r.estado = :confirmada")
    int cancelarSiConfirmada(@Param("idReserva") UUID idReserva, @Param("idTurista") UUID idTurista,
                             @Param("ahora") Instant ahora, @Param("cancelada") EstadoReserva cancelada,
                             @Param("confirmada") EstadoReserva confirmada);

    /**
     * RF52: UPDATE condicional; si una cancelación lo cambió antes, la fila ya no está Confirmada y no se toca. Abre su
     * propia transacción (o se une a la que exista). Lo acelera el índice parcial de V3.
     */
    @Transactional
    Optional<ReservaEntity> findById(UUID idReserva);
}
