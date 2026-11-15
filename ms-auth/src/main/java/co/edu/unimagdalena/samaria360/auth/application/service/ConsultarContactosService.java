package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.port.in.ConsultarContactosUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.out.ClientesServicioPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.ContactosRepositoryPort;
import co.edu.unimagdalena.samaria360.auth.domain.exception.CredencialesServicioInvalidasException;
import co.edu.unimagdalena.samaria360.auth.domain.exception.DatosRegistroInvalidosException;
import co.edu.unimagdalena.samaria360.auth.domain.model.ClienteServicio;
import co.edu.unimagdalena.samaria360.auth.domain.model.Contacto;
import co.edu.unimagdalena.samaria360.auth.domain.model.MotivoInvalido;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Contactos para un cliente interno (DEC-280): de 1 a 100 ids; solo vuelven las cuentas del rol que el cliente puede ver
 * (ms-analytics: prestadores; ms-booking: turistas).
 */
public class ConsultarContactosService implements ConsultarContactosUseCase {

    private final ClientesServicioPort clientes;
    private final ContactosRepositoryPort contactos;

    public ConsultarContactosService(ClientesServicioPort clientes, ContactosRepositoryPort contactos) {
        this.clientes = Objects.requireNonNull(clientes);
        this.contactos = Objects.requireNonNull(contactos);
    }

    @Override
    public List<Contacto> consultar(String cliente, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new DatosRegistroInvalidosException(Map.of("ids", MotivoInvalido.REQUERIDO));
        }
        if (ids.contains(null)) {
            throw new DatosRegistroInvalidosException(Map.of("ids", MotivoInvalido.FORMATO));
        }
        if (ids.size() > MAXIMO_IDS) {
            throw new DatosRegistroInvalidosException(Map.of("ids", MotivoInvalido.LONGITUD_MAXIMA));
        }
        ClienteServicio registrado = clientes.buscar(cliente).orElseThrow(CredencialesServicioInvalidasException::new);
        return contactos.buscar(new LinkedHashSet<>(ids), registrado.rolDeContactos());
    }
}
