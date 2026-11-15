package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.analytics.application.port.out.PromedioRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.domain.model.PromedioCalificacion;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Colección {@code promedios} (A002; DEC-300): {@code _id = <tipo>:<idEntidad>}, así el <i>upsert</i> es por
 * {@code _id} y dos escrituras simultáneas chocan como conflicto de escritura (la transacción se reintenta), no como
 * llave duplicada. {@code version} sube en cada cambio: ms-catalog aplica solo la mayor.
 */
@Component
public class PromedioMongoAdapter implements PromedioRepositoryPort {

    static final String COLECCION = "promedios";

    private final MongoTemplate mongo;

    public PromedioMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public long guardar(PromedioCalificacion promedio) {
        String id = promedio.tipoEntidad().name() + ":" + promedio.idEntidad();
        Update cambios = new Update()
                .set("tipoEntidad", promedio.tipoEntidad().name())
                .set("idEntidad", promedio.idEntidad())
                .set("promedio", promedio.promedio() == null ? null : promedio.promedio().doubleValue())
                .set("cantidad", promedio.cantidad())
                .inc("version", 1L);
        Document guardado = mongo.findAndModify(Query.query(Criteria.where("_id").is(id)), cambios,
                FindAndModifyOptions.options().upsert(true).returnNew(true), Document.class, COLECCION);
        return ((Number) guardado.get("version")).longValue();
    }
}
