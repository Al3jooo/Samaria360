package co.edu.unimagdalena.samaria360.auth.application.port.in;

import co.edu.unimagdalena.samaria360.auth.domain.model.Contacto;

import java.util.List;
import java.util.UUID;

/** Contactos de cuentas para un cliente interno, solo del rol que ese cliente puede ver (DEC-280). */
public interface ConsultarContactosUseCase {

    int MAXIMO_IDS = 100;

    List<Contacto> consultar(String cliente, List<UUID> ids);
}
