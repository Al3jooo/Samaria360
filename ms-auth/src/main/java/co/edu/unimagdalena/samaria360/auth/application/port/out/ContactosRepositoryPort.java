package co.edu.unimagdalena.samaria360.auth.application.port.out;

import co.edu.unimagdalena.samaria360.auth.domain.model.Contacto;
import co.edu.unimagdalena.samaria360.auth.domain.model.Rol;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Contactos de {@code auth_db}: solo las cuentas de ese rol; los ids de otro rol o inexistentes no vuelven. */
public interface ContactosRepositoryPort {

    List<Contacto> buscar(Collection<UUID> ids, Rol rol);
}
