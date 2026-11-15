package co.edu.unimagdalena.samaria360.analytics.domain.model;

import java.util.Objects;

/**
 * Contacto del Prestador para el correo de RF30, pedido a ms-auth en el momento del envío (DEC-280): no se guarda en
 * {@code analytics_db} ni viaja en los eventos.
 */
public record ContactoPrestador(String nombre, String correo) {

    public ContactoPrestador {
        Objects.requireNonNull(correo, "correo");
        nombre = nombre == null ? "" : nombre;
    }

    @Override
    public String toString() {
        return "ContactoPrestador[****]";
    }
}
