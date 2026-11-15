package co.edu.unimagdalena.samaria360.booking.application.port.out;

import co.edu.unimagdalena.samaria360.booking.domain.model.ContactoTurista;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Contactos de los turistas en ms-auth ({@code POST /internal/v1/contactos} con JWT de servicio; DEC-280, DEC-312). */
public interface ContactosTuristasPort {

    /**
     * @throws co.edu.unimagdalena.samaria360.booking.domain.exception.ContactosNoDisponiblesException si ms-auth no
     *         responde
     */
    Map<UUID, ContactoTurista> buscar(Collection<UUID> turistas);
}
