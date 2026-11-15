package co.edu.unimagdalena.samaria360.booking.application.port.out;

import co.edu.unimagdalena.samaria360.booking.domain.model.ModificacionOferta;

/** {@code OfertaModificada} en el outbox de {@code reservas_db} (V8; DEC-310), dentro de la transacción del cambio. */
public interface OutboxModificacionesPort {

    void registrar(ModificacionOferta modificacion);
}
