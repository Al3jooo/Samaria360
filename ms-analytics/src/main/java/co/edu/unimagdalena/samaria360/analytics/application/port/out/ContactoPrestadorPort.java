package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import co.edu.unimagdalena.samaria360.analytics.domain.model.ContactoPrestador;

import java.util.Optional;
import java.util.UUID;

/** Contacto del Prestador en ms-auth (endpoint interno con JWT de servicio, DEC-280). */
public interface ContactoPrestadorPort {

    /**
     * @return vacío si ms-auth no conoce al prestador
     * @throws ServicioExternoNoDisponibleException si ms-auth no responde
     */
    Optional<ContactoPrestador> buscar(UUID idPrestador);
}
