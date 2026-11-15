package co.edu.unimagdalena.samaria360.catalog.application.port.in;

import co.edu.unimagdalena.samaria360.catalog.domain.model.Afluencia;
import co.edu.unimagdalena.samaria360.catalog.domain.service.PoliticaAlternativos.Alternativo;

import java.util.List;
import java.util.UUID;

/**
 * Afluencia de los atractivos (decisión 2 del equipo; DEC-304, DEC-305): el Gestor registra la ocupación actual (RF39)
 * y la ficha pública consulta el nivel vigente, si llegó al límite (RF08) y los destinos alternativos de menor
 * concurrencia (RF51).
 */
public interface AfluenciaUseCase {

    /** {@code afluencia} nulo sin lectura vigente. */
    record AfluenciaAtractivo(Afluencia afluencia, List<Alternativo> alternativos) {
    }

    /** Pública. @throws co.edu.unimagdalena.samaria360.catalog.domain.exception.AtractivoNoEncontradoException */
    AfluenciaAtractivo consultar(String idAtractivo);

    /** Solo el Gestor. Devuelve el nivel calculado con la lectura nueva. */
    Afluencia registrar(String idAtractivo, UUID gestor, Integer ocupacion);
}
