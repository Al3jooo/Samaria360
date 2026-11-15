package co.edu.unimagdalena.samaria360.catalog.infrastructure.adapter.out.persistence.mongo;

import co.edu.unimagdalena.samaria360.catalog.application.port.out.OfertaServicioPort;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Plantilla de la oferta (RF19) y galería (RF48) de {@code servicios} (DEC-311, DEC-313): {@code updateOne}
 * condicionales sobre el dueño y el estado; el tamaño de la galería se controla en la consulta ({@code imagenes.4} no
 * existe para agregar; {@code imagenes.1} existe para quitar), así que dos cambios simultáneos no la sacan de 1 a 5.
 */
@Component
public class OfertaServicioMongoAdapter implements OfertaServicioPort {

    static final String SERVICIOS = "servicios";

    private final MongoTemplate mongo;

    public OfertaServicioMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    private static Criteria delPrestador(String idServicio, UUID prestador) {
        return Criteria.where("_id").is(new ObjectId(idServicio)).and("prestadorId").is(prestador.toString());
    }

    @Override
    public boolean fijarOferta(String idServicio, UUID prestador, List<String> diasOperacion, List<String> salidas) {
        Update cambio = new Update().set("oferta.diasOperacion", diasOperacion);
        if (salidas == null || salidas.isEmpty()) {
            cambio.unset("oferta.salidas");
        } else {
            cambio.set("oferta.salidas", salidas);
        }
        return mongo.updateFirst(Query.query(delPrestador(idServicio, prestador).and("oferta").exists(true)), cambio,
                SERVICIOS).getMatchedCount() == 1;
    }

    @Override
    public boolean agregarImagen(String idServicio, UUID prestador, String ruta) {
        return mongo.updateFirst(Query.query(delPrestador(idServicio, prestador).and("estadoPublicacion").is("Publicado")
                        .and("imagenes.4").exists(false)), new Update().push("imagenes", ruta), SERVICIOS)
                .getModifiedCount() == 1;
    }

    @Override
    public boolean quitarImagen(String idServicio, UUID prestador, String ruta) {
        return mongo.updateFirst(Query.query(delPrestador(idServicio, prestador).and("estadoPublicacion").is("Publicado")
                        .and("imagenes.1").exists(true).and("imagenes").is(ruta)), new Update().pull("imagenes", ruta),
                SERVICIOS).getModifiedCount() == 1;
    }
}
