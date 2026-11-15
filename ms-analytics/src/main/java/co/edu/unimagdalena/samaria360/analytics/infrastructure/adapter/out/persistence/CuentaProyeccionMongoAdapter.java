package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.analytics.application.port.out.CuentaProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.domain.model.CuentaProyectada;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;
import java.util.UUID;

/**
 * Proyección {@code cuentas} de analytics_db (ADR-011; decisión 6 del equipo): {@code _id} = id de la cuenta. Solo
 * avanza si la versión es mayor (como ADR-008): un evento viejo o repetido no la retrocede. Lee antes de escribir, sin
 * provocar una llave duplicada (que abortaría la transacción): el consumidor procesa de a un mensaje (ADR-011).
 */
@Component
public class CuentaProyeccionMongoAdapter implements CuentaProyeccionPort {

    static final String COLECCION = "cuentas";

    private final MongoTemplate mongo;

    public CuentaProyeccionMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void actualizarSiMayor(CuentaProyectada cuenta) {
        OptionalLong conocida = versionDe(cuenta.idUsuario());
        if (conocida.isPresent() && conocida.getAsLong() >= cuenta.version()) {
            return;
        }
        Update datos = new Update().set("rol", cuenta.rol()).set("estadoCuenta", cuenta.estadoCuenta())
                .set("version", cuenta.version());
        if (cuenta.pais() != null) {
            datos.set("pais", cuenta.pais());
        } else {
            datos.unset("pais");
        }
        mongo.upsert(Query.query(Criteria.where("_id").is(cuenta.idUsuario().toString())), datos, COLECCION);
    }

    @Override
    public OptionalLong versionDe(UUID idUsuario) {
        Document cuenta = mongo.findById(idUsuario.toString(), Document.class, COLECCION);
        if (cuenta == null || cuenta.get("version") == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(((Number) cuenta.get("version")).longValue());
    }

    @Override
    public java.util.Optional<String> estadoDe(UUID idUsuario) {
        Document cuenta = mongo.findById(idUsuario.toString(), Document.class, COLECCION);
        return cuenta == null ? java.util.Optional.empty() : java.util.Optional.ofNullable(cuenta.getString("estadoCuenta"));
    }
}
