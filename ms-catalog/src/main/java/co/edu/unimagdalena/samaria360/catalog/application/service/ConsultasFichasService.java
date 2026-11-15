package co.edu.unimagdalena.samaria360.catalog.application.service;

import co.edu.unimagdalena.samaria360.catalog.application.port.in.ConsultasFichasUseCase;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.catalog.application.port.out.RelojPort;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EventoCatalogo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DEC-283: una escritura por minuto, no una por lectura. Los conteos viven en memoria y se publican en lotes de hasta
 * 1000 entidades por evento; si ms-catalog se detiene, se pierden los del minuto en curso (pérdida aceptada).
 */
public class ConsultasFichasService implements ConsultasFichasUseCase {

    static final int MAXIMO_POR_EVENTO = 1000;

    private record Entidad(String tipo, String id, String nombre, UUID prestador) {
    }

    private final OutboxEventosPort outbox;
    private final RelojPort reloj;
    private ConcurrentHashMap<Entidad, AtomicLong> conteos = new ConcurrentHashMap<>();
    private Instant inicio;

    public ConsultasFichasService(OutboxEventosPort outbox, RelojPort reloj) {
        this.outbox = Objects.requireNonNull(outbox);
        this.reloj = Objects.requireNonNull(reloj);
        this.inicio = reloj.ahora();
    }

    @Override
    public void registrar(String tipoEntidad, String idEntidad, String nombre, UUID idPrestador) {
        if (!"servicio".equals(tipoEntidad) && !"atractivo".equals(tipoEntidad)) {
            throw new IllegalArgumentException("tipoEntidad: servicio o atractivo");
        }
        Entidad entidad = new Entidad(tipoEntidad, Objects.requireNonNull(idEntidad), nombre,
                "servicio".equals(tipoEntidad) ? idPrestador : null);
        sumar(entidad);
    }

    /** Con el mismo cerrojo que el cambio de ventana: ninguna consulta cae en un mapa ya publicado. */
    private synchronized void sumar(Entidad entidad) {
        conteos.computeIfAbsent(entidad, e -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public int publicar() {
        Map<Entidad, AtomicLong> ventana;
        Instant desde;
        Instant hasta;
        synchronized (this) {
            ventana = conteos;
            conteos = new ConcurrentHashMap<>();
            desde = inicio;
            hasta = reloj.ahora();
            inicio = hasta;
        }
        List<Map<String, Object>> lista = new ArrayList<>();
        ventana.forEach((entidad, total) -> {
            if (total.get() < 1) {
                return;
            }
            Map<String, Object> conteo = new LinkedHashMap<>();
            conteo.put("tipoEntidad", entidad.tipo());
            conteo.put("idEntidad", entidad.id());
            if (entidad.nombre() != null) {
                conteo.put("nombre", entidad.nombre());
            }
            if (entidad.prestador() != null) {
                conteo.put("idPrestador", entidad.prestador().toString());
            }
            conteo.put("consultas", total.get());
            lista.add(conteo);
        });
        int eventos = 0;
        for (int i = 0; i < lista.size(); i += MAXIMO_POR_EVENTO) {
            outbox.registrar(EventoCatalogo.consultasAgregadas(desde, hasta,
                    lista.subList(i, Math.min(lista.size(), i + MAXIMO_POR_EVENTO))));
            eventos++;
        }
        return eventos;
    }
}
