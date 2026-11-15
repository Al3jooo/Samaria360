package co.edu.unimagdalena.samaria360.auth.infrastructure.config;

import co.edu.unimagdalena.samaria360.auth.application.port.out.ClientesServicioPort;
import co.edu.unimagdalena.samaria360.auth.domain.model.ClienteServicio;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.Optional;

/**
 * Clientes internos de ms-auth (DEC-280), en {@code samaria360.clientes-servicio.<nombre>}: el secreto (de .env) y el
 * rol cuyos contactos puede leer. Sin secreto, el cliente queda deshabilitado.
 */
@ConfigurationProperties(prefix = "samaria360")
public record ClientesServicioPropiedades(Map<String, Cliente> clientesServicio) implements ClientesServicioPort {

    public record Cliente(String secreto, Rol contactos) {
    }

    public ClientesServicioPropiedades {
        clientesServicio = clientesServicio == null ? Map.of() : Map.copyOf(clientesServicio);
    }

    @Override
    public Optional<ClienteServicio> buscar(String nombre) {
        Cliente cliente = nombre == null ? null : clientesServicio.get(nombre);
        if (cliente == null || cliente.contactos() == null) {
            return Optional.empty();
        }
        return Optional.of(new ClienteServicio(nombre, cliente.secreto(), cliente.contactos()));
    }
}
