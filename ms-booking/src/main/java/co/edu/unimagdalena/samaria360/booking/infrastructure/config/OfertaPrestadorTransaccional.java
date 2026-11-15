package co.edu.unimagdalena.samaria360.booking.infrastructure.config;

import co.edu.unimagdalena.samaria360.booking.application.port.in.GestionarOfertaPrestadorUseCase;
import co.edu.unimagdalena.samaria360.booking.application.service.GestionarOfertaPrestadorService;
import co.edu.unimagdalena.samaria360.booking.domain.model.FilaPrestador;
import co.edu.unimagdalena.samaria360.booking.domain.model.OfertaReservable;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.UUID;

/**
 * RF19 (DEC-310) con las transacciones de ADR-007: la lectura del catálogo y la materialización <b>sin transacción</b>
 * (nunca hay filas bloqueadas durante una llamada HTTP); el cambio condicional y {@code OfertaModificada} en el outbox,
 * en una sola transacción.
 */
public class OfertaPrestadorTransaccional implements GestionarOfertaPrestadorUseCase {

    private final GestionarOfertaPrestadorService servicio;
    private final TransactionTemplate transaccion;

    public OfertaPrestadorTransaccional(GestionarOfertaPrestadorService servicio, TransactionTemplate transaccion) {
        this.servicio = servicio;
        this.transaccion = transaccion;
    }

    @Override
    public Inventario inventario(UUID prestador, String idServicio, LocalDate desde, LocalDate hasta) {
        return servicio.inventario(prestador, idServicio, desde, hasta);
    }

    @Override
    public FilaPrestador ajustar(ComandoFila comando) {
        OfertaReservable oferta = servicio.preparar(comando.prestador(), comando.idServicio(), comando.fecha());
        return transaccion.execute(estado -> servicio.aplicarAjuste(oferta, comando));
    }

    @Override
    public FilaPrestador cerrar(ComandoFila comando) {
        OfertaReservable oferta = servicio.preparar(comando.prestador(), comando.idServicio(), comando.fecha());
        return transaccion.execute(estado -> servicio.aplicarCierre(oferta, comando));
    }

    @Override
    public void cambiarPlantilla(ComandoPlantilla comando) {
        OfertaReservable oferta = servicio.preparar(comando.prestador(), comando.idServicio(), null);
        transaccion.executeWithoutResult(estado -> servicio.aplicarPlantilla(oferta, comando));
    }
}
