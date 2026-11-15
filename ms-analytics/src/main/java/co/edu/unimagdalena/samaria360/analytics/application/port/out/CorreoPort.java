package co.edu.unimagdalena.samaria360.analytics.application.port.out;

import co.edu.unimagdalena.samaria360.analytics.domain.model.ContactoPrestador;

import java.util.Map;

/** Correo por SMTP directo (figura 20; DEC-278, DEC-282). */
public interface CorreoPort {

    /**
     * RF30: la actividad, la fecha y la cantidad de personas.
     *
     * @throws ServicioExternoNoDisponibleException si el servidor SMTP falla
     */
    void enviarNuevaReserva(ContactoPrestador contacto, Map<String, String> datos);
}
