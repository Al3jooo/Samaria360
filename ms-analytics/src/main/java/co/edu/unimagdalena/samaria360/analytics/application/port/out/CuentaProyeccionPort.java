package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import co.edu.unimagdalena.samaria360.analytics.domain.model.CuentaProyectada;

import java.util.OptionalLong;
import java.util.UUID;

/** Proyección {@code cuentas} de analytics_db: solo avanza si la versión es mayor (ADR-008). */
public interface CuentaProyeccionPort {

    void actualizarSiMayor(CuentaProyectada cuenta);

    OptionalLong versionDe(UUID idUsuario);

    /** El estado de la cuenta proyectada, si se conoce (RF17: solo un turista Activo califica). */
    java.util.Optional<String> estadoDe(UUID idUsuario);
}
