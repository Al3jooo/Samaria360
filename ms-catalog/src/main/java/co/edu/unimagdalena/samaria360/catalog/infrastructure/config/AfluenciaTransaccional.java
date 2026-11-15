package co.edu.unimagdalena.samaria360.catalog.infrastructure.config;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.AfluenciaUseCase;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Afluencia;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Registrar la ocupación en una transacción de MongoDB (réplica rs0, DEC-281; DEC-304): la lectura actual, el
 * historial y {@code OcupacionRegistrada} en el outbox se confirman juntos. La consulta no la necesita.
 */
public class AfluenciaTransaccional implements AfluenciaUseCase {

    private final AfluenciaUseCase delegado;
    private final TransactionTemplate transaccion;

    public AfluenciaTransaccional(AfluenciaUseCase delegado, TransactionTemplate transaccion) {
        this.delegado = delegado;
        this.transaccion = transaccion;
    }

    @Override
    public AfluenciaAtractivo consultar(String idAtractivo) {
        return delegado.consultar(idAtractivo);
    }

    @Override
    public Afluencia registrar(String idAtractivo, UUID gestor, Integer ocupacion) {
        return transaccion.execute(estado -> delegado.registrar(idAtractivo, gestor, ocupacion));
    }
}
