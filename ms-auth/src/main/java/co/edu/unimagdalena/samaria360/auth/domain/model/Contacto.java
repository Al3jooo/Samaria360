package co.edu.unimagdalena.samaria360.auth.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Nombre y datos de contacto de una cuenta, para los servicios que los necesitan en el momento (DEC-280): el Prestador
 * para el correo de RF30 (ms-analytics) y el turista para el listado de asistentes de RF42 (ms-booking). Nunca viaja en
 * un evento ni se copia en otra base. {@code telefono} solo en el Prestador.
 */
public record Contacto(UUID idUsuario, Rol rol, String nombre, String correo, String telefono) {

    public Contacto {
        Objects.requireNonNull(idUsuario, "idUsuario");
        Objects.requireNonNull(rol, "rol");
        Objects.requireNonNull(correo, "correo");
        nombre = nombre == null ? "" : nombre;
    }

    @Override
    public String toString() {
        return "Contacto[" + idUsuario + ", " + rol + ", ****]";
    }
}
