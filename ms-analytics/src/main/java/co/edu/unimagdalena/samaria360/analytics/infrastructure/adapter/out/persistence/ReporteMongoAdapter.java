package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.Pagina;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.ReporteRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Reporte;
import co.edu.unimagdalena.samaria360.analytics.domain.model.TipoEntidad;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Colección {@code reportes} de analytics_db (A002; RF57, DEC-301). */
@Component
public class ReporteMongoAdapter implements ReporteRepositoryPort {

    static final String COLECCION = "reportes";

    private final MongoTemplate mongo;

    public ReporteMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean existePendiente(UUID turista, TipoEntidad tipo, String idEntidad) {
        return mongo.exists(Query.query(Criteria.where("turistaId").is(turista.toString())
                .and("tipoEntidad").is(tipo.name()).and("idEntidad").is(idEntidad)
                .and("estado").is(Reporte.Estado.Pendiente.name())), COLECCION);
    }

    @Override
    public Reporte insertar(Reporte r) {
        ObjectId id = new ObjectId();
        mongo.insert(new Document("_id", id)
                .append("tipoEntidad", r.tipoEntidad().name())
                .append("idEntidad", r.idEntidad())
                .append("turistaId", r.turistaId().toString())
                .append("motivo", r.motivo().name())
                .append("descripcion", r.descripcion())
                .append("estado", r.estado().name())
                .append("fecha", Date.from(r.fecha())), COLECCION);
        return r.conId(id.toHexString());
    }

    @Override
    public Optional<Reporte> buscar(String id) {
        return Optional.ofNullable(mongo.findById(new ObjectId(id), Document.class, COLECCION))
                .map(ReporteMongoAdapter::aDominio);
    }

    @Override
    public Pagina<Reporte> listar(Reporte.Estado estado, int pagina, int tamano) {
        Query consulta = estado == null ? new Query() : Query.query(Criteria.where("estado").is(estado.name()));
        long total = mongo.count(consulta, COLECCION);
        consulta.with(Sort.by(Sort.Direction.ASC, "fecha", "_id")).skip((long) pagina * tamano).limit(tamano);
        List<Reporte> elementos = mongo.find(consulta, Document.class, COLECCION).stream()
                .map(ReporteMongoAdapter::aDominio).toList();
        return new Pagina<>(elementos, pagina, tamano, total);
    }

    @Override
    public boolean marcarRevisado(String id, UUID administrador, Instant fecha) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(id))
                        .and("estado").is(Reporte.Estado.Pendiente.name())),
                new Update().set("estado", Reporte.Estado.Revisado.name()).set("revisadoPor", administrador.toString())
                        .set("fechaRevision", Date.from(fecha)), COLECCION).getModifiedCount() == 1;
    }

    static Reporte aDominio(Document d) {
        String revisor = d.getString("revisadoPor");
        Date revision = d.getDate("fechaRevision");
        return new Reporte(d.getObjectId("_id").toHexString(), TipoEntidad.valueOf(d.getString("tipoEntidad")),
                d.getString("idEntidad"), UUID.fromString(d.getString("turistaId")),
                Reporte.Motivo.valueOf(d.getString("motivo")), d.getString("descripcion"),
                Reporte.Estado.valueOf(d.getString("estado")), d.getDate("fecha").toInstant(),
                revisor == null ? null : UUID.fromString(revisor), revision == null ? null : revision.toInstant());
    }
}
