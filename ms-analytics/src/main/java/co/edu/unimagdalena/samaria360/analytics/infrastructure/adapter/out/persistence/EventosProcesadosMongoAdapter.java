package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.analytics.application.port.out.EventosProcesadosPort;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Colección técnica {@code eventos_procesados} (DEC-284): {@code _id} = {@code idEvento}, con TTL de 30 días (A001).
 * Se escribe en la transacción del efecto: si la transacción falla, el evento se vuelve a procesar.
 */
@Component
public class EventosProcesadosMongoAdapter implements EventosProcesadosPort {

    static final String COLECCION = "eventos_procesados";

    private final MongoTemplate mongo;

    public EventosProcesadosMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean yaProcesado(UUID idEvento) {
        return mongo.exists(Query.query(Criteria.where("_id").is(idEvento.toString())), COLECCION);
    }

    @Override
    public void registrar(UUID idEvento, String tipo, Instant procesadoEn) {
        mongo.insert(new Document("_id", idEvento.toString()).append("tipo", tipo)
                .append("procesadoEn", Date.from(procesadoEn)), COLECCION);
    }
}
