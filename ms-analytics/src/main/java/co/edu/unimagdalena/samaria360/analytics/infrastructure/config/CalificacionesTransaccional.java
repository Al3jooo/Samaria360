package co.edu.unimagdalena.samaria360.analytics.infrastructure.config;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.CalificarUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ModerarCalificacionesUseCase;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.Pagina;
import co.edu.unimagdalena.samaria360.analytics.domain.exception.ReglaNegocioException;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Calificacion;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cada escritura de RF17 y RF29 en una transacción de MongoDB (réplica rs0, DEC-281; DEC-300): la calificación, el
 * promedio, el evento del outbox y la auditoría se confirman juntos. Dos envíos simultáneos de la misma calificación
 * chocan con el índice único: la transacción se aborta y se responde {@code CALIFICACION_DUPLICADA}.
 */
public class CalificacionesTransaccional implements CalificarUseCase, ModerarCalificacionesUseCase {

    private final CalificarUseCase calificar;
    private final ModerarCalificacionesUseCase moderar;
    private final TransactionTemplate transaccion;

    public CalificacionesTransaccional(CalificarUseCase calificar, ModerarCalificacionesUseCase moderar,
                                       TransactionTemplate transaccion) {
        this.calificar = calificar;
        this.moderar = moderar;
        this.transaccion = transaccion;
    }

    @Override
    public Calificacion calificar(ComandoCalificar comando) {
        try {
            return enTransaccion(() -> calificar.calificar(comando));
        } catch (DuplicateKeyException duplicada) {
            throw new ReglaNegocioException(ReglaNegocioException.CALIFICACION_DUPLICADA);
        }
    }

    @Override
    public Elegibilidad elegibilidad(UUID turista, String tipoEntidad, String idEntidad) {
        return calificar.elegibilidad(turista, tipoEntidad, idEntidad);
    }

    @Override
    public Pagina<Calificacion> listar(String estado, int pagina) {
        return moderar.listar(estado, pagina);
    }

    @Override
    public Calificacion ocultar(String id, UUID administrador, String motivo) {
        return enTransaccion(() -> moderar.ocultar(id, administrador, motivo));
    }

    @Override
    public Calificacion mostrar(String id, UUID administrador, String motivo) {
        return enTransaccion(() -> moderar.mostrar(id, administrador, motivo));
    }

    @Override
    public void eliminar(String id, UUID administrador, String motivo) {
        enTransaccion(() -> {
            moderar.eliminar(id, administrador, motivo);
            return null;
        });
    }

    private <T> T enTransaccion(Supplier<T> accion) {
        return transaccion.execute(estado -> accion.get());
    }
}
