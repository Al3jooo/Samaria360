package co.edu.unimagdalena.samaria360.booking.domain.model;

import java.util.UUID;

/** Contacto de un turista en ms-auth (DEC-280, DEC-312): solo para el listado de RF42; no se guarda ni va a los logs. */
public record ContactoTurista(UUID idUsuario, String nombre, String correo, String telefono) {

    @Override
    public String toString() {
        return "ContactoTurista[" + idUsuario + ", ****]";
    }
}
