package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.analytics.application.port.out.NotificacionRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Notificacion;
import co.edu.unimagdalena.samaria360.analytics.domain.model.TipoNotificacion;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Colección {@code notificaciones} (Entregable 2, §2.4; RF63): {@code usuarioId}, {@code tipo}, {@code mensaje},
 * {@code leida} y {@code fecha}, más {@code datos} e {@code idEvento} (ampliación, DEC-292).
 */
@Component
public class NotificacionMongoAdapter implements NotificacionRepositoryPort {

    static final String COLECCION = "notificaciones";

    private final MongoTemplate mongo;

    public NotificacionMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void guardar(Notificacion notificacion) {
        mongo.insert(new Document("usuarioId", notificacion.usuarioId().toString())
                .append("idEvento", notificacion.idEvento().toString())
                .append("tipo", notificacion.tipo().name())
                .append("mensaje", notificacion.mensaje())
                .append("datos", new Document(new LinkedHashMap<String, Object>(notificacion.datos())))
                .append("leida", notificacion.leida())
                .append("fecha", Date.from(notificacion.fecha())), COLECCION);
    }

    @Override
    public List<Notificacion> listar(UUID usuario, int pagina, int tamano) {
        Query consulta = delUsuario(usuario).with(Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("_id")))
                .skip((long) pagina * tamano).limit(tamano);
        return mongo.find(consulta, Document.class, COLECCION).stream().map(NotificacionMongoAdapter::aDominio)
                .toList();
    }

    @Override
    public long contar(UUID usuario) {
        return mongo.count(delUsuario(usuario), COLECCION);
    }

    @Override
    public long contarNoLeidas(UUID usuario) {
        return mongo.count(Query.query(Criteria.where("usuarioId").is(usuario.toString()).and("leida").is(false)),
                COLECCION);
    }

    @Override
    public boolean marcarLeida(UUID usuario, String idNotificacion) {
        if (idNotificacion == null || !ObjectId.isValid(idNotificacion)) {
            return false;
        }
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(idNotificacion))
                        .and("usuarioId").is(usuario.toString())),
                new Update().set("leida", true), COLECCION).getMatchedCount() == 1;
    }

    @Override
    public long marcarTodas(UUID usuario) {
        return mongo.updateMulti(Query.query(Criteria.where("usuarioId").is(usuario.toString()).and("leida").is(false)),
                new Update().set("leida", true), COLECCION).getModifiedCount();
    }

    private static Query delUsuario(UUID usuario) {
        return Query.query(Criteria.where("usuarioId").is(usuario.toString()));
    }

    private static Notificacion aDominio(Document d) {
        Map<String, String> datos = new LinkedHashMap<>();
        Document guardados = d.get("datos", Document.class);
        if (guardados != null) {
            guardados.forEach((clave, valor) -> datos.put(clave, String.valueOf(valor)));
        }
        return new Notificacion(d.getObjectId("_id").toHexString(), UUID.fromString(d.getString("idEvento")),
                UUID.fromString(d.getString("usuarioId")), TipoNotificacion.valueOf(d.getString("tipo")),
                d.getString("mensaje"), datos, Boolean.TRUE.equals(d.getBoolean("leida")),
                d.getDate("fecha").toInstant());
    }
}
