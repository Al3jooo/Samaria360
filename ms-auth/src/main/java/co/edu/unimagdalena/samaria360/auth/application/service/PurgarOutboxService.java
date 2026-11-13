package co.edu.unimagdalena.samaria360.auth.application.service;

import co.edu.unimagdalena.samaria360.auth.application.port.in.PurgarOutboxUseCase;
import co.edu.unimagdalena.samaria360.auth.application.port.out.OutboxPort;
import co.edu.unimagdalena.samaria360.auth.application.port.out.RelojPort;

import java.time.Duration;
import java.util.Objects;

/**
 * Purga del outbox (DEC-272): sin ella, {@code outbox_evento} y {@code outbox_entrega} crecen sin límite (pendiente de
 * «Administrar cuentas»). Un evento se borra cuando llegó a todos sus destinos y su última entrega tiene más de
 * {@code retencion}: las proyecciones de ms-catalog y ms-booking ya tienen ese estado o uno posterior. Los pendientes no
 * se tocan, así que la purga nunca pierde un cambio sin entregar.
 */
public class PurgarOutboxService implements PurgarOutboxUseCase {

    private final OutboxPort outbox;
    private final RelojPort reloj;
    private final Duration retencion;

    public PurgarOutboxService(OutboxPort outbox, RelojPort reloj, Duration retencion) {
        this.outbox = Objects.requireNonNull(outbox);
        this.reloj = Objects.requireNonNull(reloj);
        this.retencion = Objects.requireNonNull(retencion);
        if (retencion.isNegative() || retencion.isZero()) {
            throw new IllegalArgumentException("La retención del outbox debe ser positiva");
        }
    }

    @Override
    public int purgar() {
        return outbox.purgarEntregados(reloj.ahora().minus(retencion));
    }
}
