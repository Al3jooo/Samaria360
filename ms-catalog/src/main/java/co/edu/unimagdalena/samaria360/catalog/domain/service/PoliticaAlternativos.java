package co.edu.unimagdalena.samaria360.catalog.domain.service;

import co.edu.unimagdalena.samaria360.catalog.domain.model.Afluencia;
import co.edu.unimagdalena.samaria360.catalog.domain.model.AtractivoOcupacion;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EstadoOperativo;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Destinos alternativos de menor concurrencia (RF51, y RF08 cuando el atractivo llega a su límite; decisión 2 del
 * equipo, DEC-305; **decisión del equipo, no del SRS**): «similar» es de la misma categoría. Se excluyen el propio
 * atractivo y los que no están abiertos (RF33); si el de origen tiene afluencia vigente, solo los de menor porcentaje.
 * Orden: menor ocupación primero (los que no tienen lectura vigente, al final) y, a igual ocupación, el más cercano.
 * Hasta 3, las tarjetas de WF-06.
 */
public class PoliticaAlternativos {

    public static final int MAXIMO = 3;

    public record Alternativo(AtractivoOcupacion atractivo, Afluencia afluencia, double distanciaKm) {
    }

    public List<Alternativo> sugerir(AtractivoOcupacion origen, List<AtractivoOcupacion> candidatos, Instant ahora) {
        Objects.requireNonNull(origen, "origen");
        Optional<Afluencia> propia = Afluencia.calcular(origen.lectura(), ahora);
        return candidatos.stream()
                .filter(c -> !c.id().equals(origen.id()))
                .filter(c -> Objects.equals(c.categoria(), origen.categoria()))
                .filter(c -> c.estadoOperativo() == null || c.estadoOperativo() == EstadoOperativo.Abierto)
                .map(c -> new Alternativo(c, Afluencia.calcular(c.lectura(), ahora).orElse(null),
                        origen.coordenada().distanciaKm(c.coordenada())))
                .filter(a -> propia.isEmpty() || (a.afluencia() != null
                        && a.afluencia().porcentaje() < propia.get().porcentaje()))
                .sorted(Comparator.comparing((Alternativo a) -> a.afluencia() == null ? Integer.MAX_VALUE
                                : a.afluencia().porcentaje())
                        .thenComparingDouble(Alternativo::distanciaKm)
                        .thenComparing(a -> a.atractivo().id()))
                .limit(MAXIMO)
                .toList();
    }
}
