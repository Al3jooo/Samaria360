package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.analytics.application.port.out.OcupacionProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.domain.model.OcupacionRegistrada;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Colección {@code ocupaciones} de analytics_db (A003; DEC-304). Lee antes de escribir (una llave duplicada abortaría
 * la transacción del evento).
 */
@Component
public class OcupacionProyeccionMongoAdapter implements OcupacionProyeccionPort {

    static final String COLECCION = "ocupaciones";

    private final MongoTemplate mongo;

    public OcupacionProyeccionMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void guardar(OcupacionRegistrada o) {
        if (mongo.exists(Query.query(Criteria.where("idEvento").is(o.idEvento().toString())), COLECCION)) {
            return;
        }
        Document documento = new Document("idEvento", o.idEvento().toString())
                .append("idAtractivo", o.idAtractivo())
                .append("nombreAtractivo", o.nombreAtractivo())
                .append("ocupacion", o.ocupacion())
                .append("limite", o.limite())
                .append("porcentaje", o.porcentaje())
                .append("nivel", o.nivel())
                .append("fecha", Date.from(o.fecha()));
        if (o.categoria() != null) {
            documento.append("categoria", o.categoria());
        }
        mongo.insert(documento, COLECCION);
    }
}
