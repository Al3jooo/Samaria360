package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.out.persistence.mongo;

import co.edu.unimagdalena.samaria360.catalog.application.port.out.CalificacionCatalogoPort;
import co.edu.unimagdalena.samaria360.catalog.domain.model.CalificacionEntidad;
import co.edu.unimagdalena.samaria360.catalog.domain.model.TipoOferta;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * {@code calificacion} de {@code servicios} y {@code atractivos} (M015; DEC-302): un {@code updateOne} condicional por
 * {@code _id} y versión, idempotente. No toca la {@code version} del documento (la de la edición, DEC-186 y DEC-207).
 */
@Component
public class CalificacionCatalogoMongoAdapter implements CalificacionCatalogoPort {

    private final MongoTemplate mongo;

    public CalificacionCatalogoMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean guardarSiMayor(CalificacionEntidad c) {
        String coleccion = c.tipo() == TipoOferta.Servicio ? "servicios" : "atractivos";
        Query condicion = Query.query(Criteria.where("_id").is(new ObjectId(c.idEntidad())).orOperator(
                Criteria.where("calificacion").exists(false),
                Criteria.where("calificacion.version").lt(c.version())));
        Document calificacion = new Document("promedio", c.promedio()).append("cantidad", c.cantidad())
                .append("version", c.version());
        return mongo.updateFirst(condicion, new Update().set("calificacion", calificacion), coleccion)
                .getModifiedCount() == 1;
    }
}
