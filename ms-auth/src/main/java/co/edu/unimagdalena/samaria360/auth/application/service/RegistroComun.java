package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.port.out.NotificadorCuentaPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RegistroRepositoryPort;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/** Pasos compartidos por los dos registros (ADR-006). */
final class RegistroComun {

    /** {@code usuario.fecha_registro} es una fecha: se toma en la hora de Santa Marta. */
    static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private RegistroComun() {
    }

    static LocalDate hoy(RelojPort reloj) {
        return LocalDate.ofInstant(reloj.ahora(), ZONA);
    }

    /**
     * Si el correo ya tiene cuenta, avisa a su dueño y devuelve true: el registro termina con la misma respuesta que uno
     * nuevo, sin crear nada.
     */
    static boolean atenderCorreoExistente(RegistroRepositoryPort registros, NotificadorCuentaPort notificador,
                                          String correo) {
        Optional<UUID> existente = registros.buscarIdPorCorreo(correo);
        existente.ifPresent(id -> notificador.avisarRegistroConCorreoExistente(id, correo));
        return existente.isPresent();
    }
}
