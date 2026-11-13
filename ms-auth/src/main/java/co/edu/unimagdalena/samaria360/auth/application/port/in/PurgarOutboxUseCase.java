package co.edu.unimagdalena.samaria360.auth.application.port.in;

/** Retención del outbox de cuentas (ADR-008, DEC-272): borra los eventos ya entregados a todos sus destinos. */
public interface PurgarOutboxUseCase {

    /** @return cuántos eventos se borraron */
    int purgar();
}
