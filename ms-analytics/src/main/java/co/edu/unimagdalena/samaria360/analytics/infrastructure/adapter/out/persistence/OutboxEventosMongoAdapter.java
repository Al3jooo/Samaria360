package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.analytics.application.port.out.OutboxEventosPort;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.PublicacionPendiente;
import co.edu.unimagdalena.samaria360.analytics.domain.model.EventoAnalitica;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Outbox de {@code analytics_db} (colección {@code outbox_eventos}, A002; ADR-011, DEC-300). {@link #registrar} se
 * ejecuta en la transacción de MongoDB que abre el caso de uso (réplica rs0, DEC-281). El relevo reclama cada documento
 * con un {@code findAndModify} atómico que le concede un arriendo: dos instancias no publican el mismo.
 */
@Component
public class OutboxEventosMongoAdapter implements OutboxEventosPort {

    static final String COLECCION = "outbox_eventos";

    private final MongoTemplate mongo;

    public OutboxEventosMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void registrar(EventoAnalitica evento) {
        Document documento = new Document("idPublico", UUID.randomUUID().toString())
                .append("tipo", evento.tipo())
                .append("clave", evento.clave())
                .append("datos", new Document(evento.datos()))
                .append("creadoEn", Date.from(evento.ocurridoEn()))
                .append("intentos", 0)
                .append("proximoIntento", Date.from(evento.ocurridoEn()));
        mongo.insert(documento, COLECCION);
    }

    @Override
    public List<PublicacionPendiente> reclamar(Instant ahora, Duration arriendo, int limite) {
        List<PublicacionPendiente> reclamadas = new ArrayList<>();
        Query pendiente = Query.query(Criteria.where("publicadoEn").exists(false)
                .and("proximoIntento").lte(Date.from(ahora))).with(Sort.by("_id"));
        Update conArriendo = new Update().set("proximoIntento", Date.from(ahora.plus(arriendo)));
        for (int i = 0; i < limite; i++) {
            Document documento = mongo.findAndModify(pendiente, conArriendo,
                    FindAndModifyOptions.options().returnNew(false), Document.class, COLECCION);
            if (documento == null) {
                break;
            }
            reclamadas.add(new PublicacionPendiente(documento.getObjectId("_id").toHexString(),
                    UUID.fromString(documento.getString("idPublico")), documento.getString("tipo"),
                    documento.getString("clave"), documento.getDate("creadoEn").toInstant(),
                    documento.get("datos", Document.class).toJson(), documento.getInteger("intentos", 0)));
        }
        return reclamadas;
    }

    @Override
    public void marcarPublicado(PublicacionPendiente publicacion, Instant ahora) {
        mongo.updateFirst(porId(publicacion), new Update().set("publicadoEn", Date.from(ahora)), COLECCION);
    }

    @Override
    public void registrarFallo(PublicacionPendiente publicacion, Instant proximoIntento, String error) {
        mongo.updateFirst(porId(publicacion), new Update().inc("intentos", 1)
                .set("proximoIntento", Date.from(proximoIntento)).set("ultimoError", error), COLECCION);
    }

    @Override
    public void liberar(List<PublicacionPendiente> publicaciones, Instant ahora) {
        for (PublicacionPendiente publicacion : publicaciones) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(publicacion.idEvento()))
                            .and("publicadoEn").exists(false)),
                    new Update().set("proximoIntento", Date.from(ahora)), COLECCION);
        }
    }

    @Override
    public int purgarPublicados(Instant limite) {
        return (int) mongo.remove(Query.query(Criteria.where("publicadoEn").lt(Date.from(limite))), COLECCION)
                .getDeletedCount();
    }

    private static Query porId(PublicacionPendiente publicacion) {
        return Query.query(Criteria.where("_id").is(new ObjectId(publicacion.idEvento())));
    }
}
