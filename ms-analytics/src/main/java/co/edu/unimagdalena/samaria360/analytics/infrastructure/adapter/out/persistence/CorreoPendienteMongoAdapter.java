package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.analytics.application.port.out.CorreoPendienteRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.domain.model.CorreoPendiente;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cola de correos de RF30 (colección técnica {@code correos_pendientes}, DEC-292). Sin direcciones de correo: el
 * contacto se pide a ms-auth al enviar (DEC-280). Cada correo se reclama con un {@code findAndModify} atómico.
 */
@Component
public class CorreoPendienteMongoAdapter implements CorreoPendienteRepositoryPort {

    static final String COLECCION = "correos_pendientes";

    private final MongoTemplate mongo;

    public CorreoPendienteMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void encolar(CorreoPendiente correo) {
        mongo.insert(new Document("idEvento", correo.idEvento().toString())
                .append("idPrestador", correo.idPrestador().toString())
                .append("datos", new Document(new LinkedHashMap<String, Object>(correo.datos())))
                .append("intentos", correo.intentos())
                .append("proximoIntento", Date.from(correo.proximoIntento())), COLECCION);
    }

    @Override
    public List<CorreoPendiente> reclamar(Instant ahora, Duration arriendo, int limite) {
        List<CorreoPendiente> reclamados = new ArrayList<>();
        Query pendiente = Query.query(Criteria.where("enviadoEn").exists(false)
                .and("proximoIntento").lte(Date.from(ahora))).with(Sort.by("_id"));
        Update conArriendo = new Update().set("proximoIntento", Date.from(ahora.plus(arriendo)));
        for (int i = 0; i < limite; i++) {
            Document d = mongo.findAndModify(pendiente, conArriendo, FindAndModifyOptions.options().returnNew(false),
                    Document.class, COLECCION);
            if (d == null) {
                break;
            }
            Map<String, String> datos = new LinkedHashMap<>();
            d.get("datos", Document.class).forEach((clave, valor) -> datos.put(clave, String.valueOf(valor)));
            reclamados.add(new CorreoPendiente(d.getObjectId("_id").toHexString(),
                    UUID.fromString(d.getString("idEvento")), UUID.fromString(d.getString("idPrestador")), datos,
                    d.getInteger("intentos", 0), d.getDate("proximoIntento").toInstant()));
        }
        return reclamados;
    }

    @Override
    public void marcarEnviado(CorreoPendiente correo, Instant ahora) {
        mongo.updateFirst(porId(correo), new Update().set("enviadoEn", Date.from(ahora)), COLECCION);
    }

    @Override
    public void registrarFallo(CorreoPendiente correo, Instant proximoIntento, String error) {
        String texto = error == null ? "sin detalle" : (error.length() > 500 ? error.substring(0, 500) : error);
        mongo.updateFirst(porId(correo), new Update().inc("intentos", 1)
                .set("proximoIntento", Date.from(proximoIntento)).set("ultimoError", texto), COLECCION);
    }

    private static Query porId(CorreoPendiente correo) {
        return Query.query(Criteria.where("_id").is(new ObjectId(correo.id())));
    }
}
