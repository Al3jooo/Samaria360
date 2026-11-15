package co.edu.unimagdalena.samaria360.auth.application.port.out;

import co.edu.unimagdalena.samaria360.auth.domain.model.ClienteServicio;

import java.util.Optional;

/** Clientes internos registrados (configuración; los secretos van en .env). */
public interface ClientesServicioPort {

    Optional<ClienteServicio> buscar(String nombre);
}
