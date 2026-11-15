package co.edu.unimagdalena.samaria360.catalog.infrastructure.config;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.AdministrarServiciosUseCase;
import co.edu.unimagdalena.samaria360.catalog.domain.model.PaginaResultados;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ServicioComercial;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * La decisión del Administrador (RF21) y su evento {@code ServicioDecidido} en una sola transacción de MongoDB
 * (ADR-011, DEC-293; réplica rs0, DEC-281). Las lecturas no abren transacción.
 */
public class AdministrarServiciosTransaccional implements AdministrarServiciosUseCase {

    private final AdministrarServiciosUseCase delegado;
    private final TransactionTemplate transaccion;

    public AdministrarServiciosTransaccional(AdministrarServiciosUseCase delegado, TransactionTemplate transaccion) {
        this.delegado = delegado;
        this.transaccion = transaccion;
    }

    @Override
    public PaginaResultados<ServicioComercial> listar(String estado, int pagina) {
        return delegado.listar(estado, pagina);
    }

    @Override
    public ServicioEnRevision detalle(String id) {
        return delegado.detalle(id);
    }

    @Override
    public ServicioEnRevision decidir(String id, String decision, String justificacion, UUID administrador) {
        return transaccion.execute(estado -> delegado.decidir(id, decision, justificacion, administrador));
    }
}
