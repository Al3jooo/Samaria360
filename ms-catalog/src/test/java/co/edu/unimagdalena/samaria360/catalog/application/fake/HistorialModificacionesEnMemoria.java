package co.edu.unimagdalena.samaria360.catalog.application.fake;

import co.edu.unimagdalena.samaria360.catalog.application.port.out.HistorialModificacionesPort;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ModificacionServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.PaginaResultados;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** {@code historial_modificaciones} en memoria (RF41), idempotente por {@code idEvento} como el índice de M017. */
public class HistorialModificacionesEnMemoria implements HistorialModificacionesPort {

    private final List<ModificacionServicio> entradas = new ArrayList<>();

    public List<ModificacionServicio> todas() {
        return List.copyOf(entradas);
    }

    @Override
    public void registrar(ModificacionServicio m) {
        if (m.idEvento() != null && entradas.stream().anyMatch(e -> m.idEvento().equals(e.idEvento()))) {
            return;
        }
        entradas.add(m);
    }

    @Override
    public PaginaResultados<ModificacionServicio> listar(UUID prestador, String idServicio, Instant desde, int pagina,
                                                         int tamano) {
        List<ModificacionServicio> filtradas = entradas.stream()
                .filter(e -> e.prestadorId().equals(prestador))
                .filter(e -> idServicio == null || e.idServicio().equals(idServicio))
                .filter(e -> !e.fecha().isBefore(desde))
                .sorted(Comparator.comparing(ModificacionServicio::fecha).reversed())
                .toList();
        List<ModificacionServicio> pag = filtradas.stream().skip((long) pagina * tamano).limit(tamano).toList();
        return new PaginaResultados<>(pag, pagina, tamano, filtradas.size());
    }
}
