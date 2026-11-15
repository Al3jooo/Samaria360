package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.analytics.application.port.out.ConsultasProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.domain.model.ConsultasFicha;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Colección {@code consultas} de analytics_db (A004; DEC-308): un documento por ficha y día,
 * {@code _id = <tipo>:<idEntidad>:<dia>}. Lee antes de escribir: una llave duplicada abortaría la transacción.
 */
@Component
public class ConsultasProyeccionMongoAdapter implements ConsultasProyeccionPort {

    static final String COLECCION = "consultas";

    private final MongoTemplate mongo;

    public ConsultasProyeccionMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void sumar(ConsultasFicha c) {
        String id = c.tipoEntidad().name() + ":" + c.idEntidad() + ":" + c.dia();
        Query porId = Query.query(Criteria.where("_id").is(id));
        if (mongo.exists(porId, COLECCION)) {
            Update cambio = new Update().inc("consultas", c.consultas());
            if (c.nombre() != null) {
                cambio.set("nombre", c.nombre());
            }
            mongo.updateFirst(porId, cambio, COLECCION);
            return;
        }
        Document documento = new Document("_id", id)
                .append("tipoEntidad", c.tipoEntidad().name())
                .append("idEntidad", c.idEntidad())
                .append("dia", c.dia().toString())
                .append("consultas", c.consultas());
        if (c.nombre() != null) {
            documento.append("nombre", c.nombre());
        }
        if (c.idPrestador() != null) {
            documento.append("idPrestador", c.idPrestador().toString());
        }
        mongo.insert(documento, COLECCION);
    }
}
