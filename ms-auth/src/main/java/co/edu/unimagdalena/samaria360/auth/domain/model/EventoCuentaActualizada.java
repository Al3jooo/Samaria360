package co.edu.unimagdalena.samaria360.auth.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Hecho que ms-auth entrega a ms-catalog y ms-booking (ADR-008) y publica en RabbitMQ (ADR-011): la cuenta {@code idUsuario}, de rol {@code rol}, quedó
 * en el estado {@code estadoCuenta} (el derivado) con la versión {@code version}.
 * <p>
 * {@code rnt} es el Registro Nacional de Turismo del prestador (DEC-126, DEC-146): obligatorio si el rol es Prestador y
 * nulo en cualquier otro rol. ms-catalog guarda una copia en su proyección para los servicios que se registren (HC2).
 */
public record EventoCuentaActualizada(UUID idUsuario, Rol rol, EstadoAdministrativo estadoCuenta, long version,
                                      String rnt, Instant ocurridoEn) {

    public EventoCuentaActualizada {
        Objects.requireNonNull(idUsuario, "idUsuario");
        Objects.requireNonNull(rol, "rol");
        Objects.requireNonNull(estadoCuenta, "estadoCuenta");
        Objects.requireNonNull(ocurridoEn, "ocurridoEn");
        if (version < 0) {
            throw new IllegalArgumentException("version no puede ser negativa");
        }
        if (rol == Rol.Prestador && (rnt == null || rnt.isBlank())) {
            throw new IllegalArgumentException("El evento de un prestador lleva su RNT");
        }
        if (rol != Rol.Prestador && rnt != null) {
            throw new IllegalArgumentException("Solo el evento de un prestador lleva RNT");
        }
    }

    /**
     * Cuenta recién registrada (RF01, RF02): versión 0, solo para RabbitMQ (ms-analytics cuenta los turistas por país,
     * RF14). Los destinos HTTP no la reciben: aceptan una cuenta sin fila (DEC-110) y su contrato empieza en 1.
     */
    public static EventoCuentaActualizada registro(UUID idUsuario, Rol rol, EstadoAdministrativo estado, String rnt,
                                                   Instant ahora) {
        return new EventoCuentaActualizada(idUsuario, rol, estado, 0, rnt, ahora);
    }

    public static EventoCuentaActualizada de(CambioEstadoCuenta cambio, Instant ahora) {
        return new EventoCuentaActualizada(cambio.idUsuario(), cambio.rol(), cambio.estadoNuevo(),
                cambio.versionNueva(), cambio.rnt(), ahora);
    }

    /**
     * Cambio de rol (RF45): el rol nuevo; el estado sigue Activo porque solo una cuenta Activa cambia de rol (DEC-105).
     * El Prestador no cambia de rol (DEC-82), así que este evento nunca lleva RNT.
     */
    public static EventoCuentaActualizada de(CambioRol cambio, Instant ahora) {
        return new EventoCuentaActualizada(cambio.idUsuario(), cambio.rolNuevo(), EstadoAdministrativo.Activo,
                cambio.versionNueva(), null, ahora);
    }
}
