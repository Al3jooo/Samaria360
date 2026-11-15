package co.edu.unimagdalena.samaria360.catalog.application.service;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.AfluenciaUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.OcupacionPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.AtractivoNoEncontradoException;
import co.edu.unimagdalena.samaria360.catalog.domain.exception.ContenidoInvalidoException;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Afluencia;
import co.edu.unimagdalena.samaria360.catalog.domain.model.AtractivoOcupacion;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EventoCatalogo;
import co.edu.unimagdalena.samaria360.catalog.domain.model.IdentificadorObjeto;
import co.edu.unimagdalena.samaria360.catalog.domain.model.MotivoInvalido;
import co.edu.unimagdalena.samaria360.catalog.domain.service.PoliticaAlternativos;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * RF39, RF08 y RF51 (decisión 2 del equipo; DEC-304, DEC-305). Registrar la ocupación fija la lectura actual, la
 * agrega al historial y encola {@code OcupacionRegistrada} (RF64, en ms-analytics) en la misma transacción de MongoDB,
 * que abre la infraestructura. El atractivo debe tener su límite de capacidad de carga (RF07).
 */
public class AfluenciaService implements AfluenciaUseCase {

    private final OcupacionPort ocupaciones;
    private final OutboxEventosPort outbox;
    private final RelojPort reloj;
    private final PoliticaAlternativos politica = new PoliticaAlternativos();

    public AfluenciaService(OcupacionPort ocupaciones, OutboxEventosPort outbox, RelojPort reloj) {
        this.ocupaciones = Objects.requireNonNull(ocupaciones);
        this.outbox = Objects.requireNonNull(outbox);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public AfluenciaAtractivo consultar(String idAtractivo) {
        AtractivoOcupacion atractivo = existente(idAtractivo);
        Instant ahora = reloj.ahora();
        List<AtractivoOcupacion> candidatos = atractivo.categoria() == null ? List.of()
                : ocupaciones.deCategoria(atractivo.categoria());
        return new AfluenciaAtractivo(Afluencia.calcular(atractivo.lectura(), ahora).orElse(null),
                politica.sugerir(atractivo, candidatos, ahora));
    }

    @Override
    public Afluencia registrar(String idAtractivo, UUID gestor, Integer ocupacion) {
        int valor = Afluencia.ocupacionValida(ocupacion);
        AtractivoOcupacion atractivo = existente(idAtractivo);
        Integer limite = atractivo.lectura() == null ? null : atractivo.lectura().limite();
        if (limite == null || limite < 1) {
            // Sin límite de capacidad de carga (RF07) no hay porcentaje que calcular.
            throw new ContenidoInvalidoException(Map.of("ocupacion", MotivoInvalido.NO_PERMITIDO));
        }
        Instant ahora = reloj.ahora();
        if (!ocupaciones.registrar(idAtractivo, valor, limite, ahora, gestor)) {
            throw new AtractivoNoEncontradoException(idAtractivo);
        }
        Afluencia afluencia = Afluencia.calcular(new Afluencia.Lectura(valor, ahora, limite), ahora).orElseThrow();
        outbox.registrar(EventoCatalogo.ocupacionRegistrada(atractivo, afluencia));
        return afluencia;
    }

    private AtractivoOcupacion existente(String id) {
        if (!IdentificadorObjeto.esValido(id)) {
            throw new AtractivoNoEncontradoException(id);
        }
        return ocupaciones.buscar(id).orElseThrow(() -> new AtractivoNoEncontradoException(id));
    }
}
