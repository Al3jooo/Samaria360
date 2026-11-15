package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.out.persistence.mongo;

import co.edu.unimagdalena.samaria360.catalog.application.port.out.OcupacionPort;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Afluencia;
import co.edu.unimagdalena.samaria360.catalog.domain.model.AtractivoOcupacion;
import co.edu.unimagdalena.samaria360.catalog.domain.model.Coordenada;
import co.edu.unimagdalena.samaria360.catalog.domain.model.EstadoOperativo;
import org.bson.Document;
import org.bson.types.ObjectId;
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

/**
 * Ocupación de los atractivos (M016; DEC-304): {@code ocupacionActual {valor, fecha}} en {@code atractivos} (sin tocar
 * la {@code version} de la edición del Gestor) y el historial en {@code ocupaciones}. Por {@code MongoTemplate}, que
 * usa la sesión de la transacción del caso de uso.
 */
@Component
public class OcupacionMongoAdapter implements OcupacionPort {

    static final String ATRACTIVOS = "atractivos";
    static final String HISTORIAL = "ocupaciones";

    private final MongoTemplate mongo;

    public OcupacionMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Optional<AtractivoOcupacion> buscar(String id) {
        return Optional.ofNullable(mongo.findById(new ObjectId(id), Document.class, ATRACTIVOS))
                .map(OcupacionMongoAdapter::aDominio);
    }

    @Override
    public List<AtractivoOcupacion> deCategoria(String categoria) {
        return mongo.find(Query.query(Criteria.where("categoria").is(categoria)), Document.class, ATRACTIVOS).stream()
                .map(OcupacionMongoAdapter::aDominio).toList();
    }

    @Override
    public boolean registrar(String id, int ocupacion, int limite, Instant fecha, UUID gestor) {
        boolean existe = mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(id))),
                new Update().set("ocupacionActual", new Document("valor", ocupacion).append("fecha", Date.from(fecha))),
                ATRACTIVOS).getMatchedCount() == 1;
        if (existe) {
            mongo.insert(new Document("atractivoId", id).append("ocupacion", ocupacion).append("limite", limite)
                    .append("fecha", Date.from(fecha)).append("gestorId", gestor.toString()), HISTORIAL);
        }
        return existe;
    }

    @SuppressWarnings("unchecked")
    static AtractivoOcupacion aDominio(Document d) {
        Document punto = d.get("coordenadas", Document.class);
        List<Number> coordenadas = (List<Number>) punto.get("coordinates");
        List<String> imagenes = d.getList("imagenes", String.class);
        Document actual = d.get("ocupacionActual", Document.class);
        Number limite = (Number) d.get("limiteCapacidadCarga");
        Afluencia.Lectura lectura = actual == null
                ? new Afluencia.Lectura(0, null, limite == null ? null : limite.intValue())
                : new Afluencia.Lectura(((Number) actual.get("valor")).intValue(), actual.getDate("fecha").toInstant(),
                limite == null ? null : limite.intValue());
        String estado = d.getString("estadoOperativo");
        return new AtractivoOcupacion(d.getObjectId("_id").toHexString(), d.getString("nombre"),
                d.getString("categoria"), imagenes == null || imagenes.isEmpty() ? null : imagenes.get(0),
                new Coordenada(coordenadas.get(0).doubleValue(), coordenadas.get(1).doubleValue()),
                estado == null ? null : EstadoOperativo.valueOf(estado), lectura);
    }
}
