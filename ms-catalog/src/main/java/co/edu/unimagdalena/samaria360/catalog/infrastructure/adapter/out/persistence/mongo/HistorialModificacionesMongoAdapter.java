package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.out.persistence.mongo;

import co.edu.unimagdalena.samaria360.catalog.application.port.out.HistorialModificacionesPort;
import co.edu.unimagdalena.samaria360.catalog.domain.model.ModificacionServicio;
import co.edu.unimagdalena.samaria360.catalog.domain.model.PaginaResultados;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Colección {@code historial_modificaciones} (M017; RF41, DEC-311). Lee antes de escribir por {@code idEvento}: un
 * {@code OfertaModificada} entregado dos veces queda una sola vez.
 */
@Component
public class HistorialModificacionesMongoAdapter implements HistorialModificacionesPort {

    static final String COLECCION = "historial_modificaciones";

    private final MongoTemplate mongo;

    public HistorialModificacionesMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void registrar(ModificacionServicio m) {
        if (m.idEvento() != null
                && mongo.exists(Query.query(Criteria.where("idEvento").is(m.idEvento())), COLECCION)) {
            return;
        }
        Document documento = new Document("servicioId", m.idServicio())
                .append("prestadorId", m.prestadorId().toString())
                .append("tipo", m.tipo().name())
                .append("valorNuevo", m.valorNuevo())
                .append("fecha", Date.from(m.fecha()));
        if (m.nombreServicio() != null) {
            documento.append("nombreServicio", m.nombreServicio());
        }
        if (m.detalle() != null) {
            documento.append("detalle", m.detalle());
        }
        if (m.valorAnterior() != null) {
            documento.append("valorAnterior", m.valorAnterior());
        }
        if (m.idEvento() != null) {
            documento.append("idEvento", m.idEvento());
        }
        mongo.insert(documento, COLECCION);
    }

    @Override
    public PaginaResultados<ModificacionServicio> listar(UUID prestador, String idServicio, Instant desde, int pagina,
                                                         int tamano) {
        Criteria criterios = Criteria.where("prestadorId").is(prestador.toString()).and("fecha").gte(Date.from(desde));
        if (idServicio != null) {
            criterios.and("servicioId").is(idServicio);
        }
        Query consulta = Query.query(criterios);
        long total = mongo.count(consulta, COLECCION);
        consulta.with(Sort.by(Sort.Direction.DESC, "fecha", "_id")).skip((long) pagina * tamano).limit(tamano);
        List<ModificacionServicio> elementos = mongo.find(consulta, Document.class, COLECCION).stream()
                .map(d -> new ModificacionServicio(d.getString("servicioId"), UUID.fromString(d.getString("prestadorId")),
                        d.getString("nombreServicio"), ModificacionServicio.Tipo.valueOf(d.getString("tipo")),
                        d.getString("detalle"), d.getString("valorAnterior"), d.getString("valorNuevo"),
                        d.getDate("fecha").toInstant(), d.getString("idEvento")))
                .toList();
        return new PaginaResultados<>(elementos, pagina, tamano, total);
    }
}
