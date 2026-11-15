package co.edu.unimagdalena.samaria360.analytics.domain.model;

import co.edu.unimagdalena.samaria360.analytics.domain.exception.EventoInvalidoException;

import java.util.Objects;
import java.util.UUID;

/**
 * Proyección de una cuenta de ms-auth, alimentada por {@code CuentaActualizada} (ADR-011; decisión 6 del equipo: sin
 * leer bases ajenas). Sirve al validador del claim {@code ver} (DEC-110) y, con {@code pais}, a «visitantes por zona»
 * (RF14). {@code pais} solo en el turista.
 */
public record CuentaProyectada(UUID idUsuario, String rol, String estadoCuenta, long version, String pais) {

    public CuentaProyectada {
        Objects.requireNonNull(idUsuario, "idUsuario");
        Objects.requireNonNull(rol, "rol");
        Objects.requireNonNull(estadoCuenta, "estadoCuenta");
        if (version < 0) {
            throw new IllegalArgumentException("version no puede ser negativa");
        }
    }

    public static CuentaProyectada de(EventoEntrante evento) {
        String rol = evento.texto("rol");
        try {
            RolCuenta.valueOf(rol);
        } catch (IllegalArgumentException e) {
            throw new EventoInvalidoException("CuentaActualizada: rol desconocido " + rol);
        }
        long version = evento.entero("version");
        if (version < 0) {
            throw new EventoInvalidoException("CuentaActualizada: version negativa");
        }
        String pais = RolCuenta.Turista.name().equals(rol) ? evento.textoOpcional("pais").orElse(null) : null;
        return new CuentaProyectada(evento.uuid("idUsuario"), rol, evento.texto("estadoCuenta"), version, pais);
    }
}
