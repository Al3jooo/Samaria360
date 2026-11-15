package co.edu.unimagdalena.samaria360.catalog.application.service;

import co.edu.unimagdalena.samaria360.catalog.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.in.BuscarOfertaUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.OfertaRepositoryPort;
import co.edu.unimagdalena.samaria360.catalog.domain.model.CriteriosBusqueda;
import co.edu.unimagdalena.samaria360.catalog.domain.model.OrdenBusqueda;
import co.edu.unimagdalena.samaria360.catalog.domain.model.PaginaResultados;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ResultadoOferta;

import java.util.Objects;

public class BuscarOfertaService implements BuscarOfertaUseCase {

    private final OfertaRepositoryPort ofertas;
    private final RelojPort reloj;

    public BuscarOfertaService(OfertaRepositoryPort ofertas) {
        this(ofertas, java.time.Instant::now);
    }

    /** @param reloj para el nivel de afluencia vigente de los atractivos (RF39, DEC-304) */
    public BuscarOfertaService(OfertaRepositoryPort ofertas, RelojPort reloj) {
        this.ofertas = Objects.requireNonNull(ofertas);
        this.reloj = Objects.requireNonNull(reloj);
    }

    @Override
    public PaginaResultados<ResultadoOferta> buscar(CriteriosBusqueda criterios) {
        Objects.requireNonNull(criterios, "criterios");
        PaginaResultados<ResultadoOferta> pagina = ofertas.buscar(
                criterios.orden() != null ? criterios : criterios.conOrden(ordenPorDefecto(criterios)));
        java.time.Instant ahora = reloj.ahora();
        return new PaginaResultados<>(pagina.elementos().stream().map(r -> r.conAfluencia(ahora)).toList(),
                pagina.pagina(), pagina.tamano(), pagina.totalElementos());
    }

    /** Por distancia si el turista compartió su ubicación (RF38); si no, por nombre. */
    static OrdenBusqueda ordenPorDefecto(CriteriosBusqueda criterios) {
        return criterios.ubicacion() != null ? OrdenBusqueda.Distancia : OrdenBusqueda.Nombre;
    }
}
