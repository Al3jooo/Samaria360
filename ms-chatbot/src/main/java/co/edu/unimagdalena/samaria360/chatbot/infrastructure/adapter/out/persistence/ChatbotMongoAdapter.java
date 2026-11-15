package co.edu.unimagdalena.samaria360.chatbot.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.chatbot.application.port.out.ConocimientoRepositoryPort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.CuentaProyeccionPort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.ItinerarioRepositoryPort;
import co.edu.unimagdalena.samaria360.chatbot.application.port.out.UsoDiarioPort;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.EntradaConocimiento;
import co.edu.unimagdalena.samaria360.chatbot.domain.model.Itinerario;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * chatbot_db (DEC-297): {@code base_conocimiento_turistico} e {@code itinerarios_sugeridos} (Entregable 2, §2.4), y las
 * técnicas {@code uso_diario} y {@code cuentas}. Un id que no es un ObjectId no existe.
 */
@Component
public class ChatbotMongoAdapter implements ConocimientoRepositoryPort, ItinerarioRepositoryPort, UsoDiarioPort,
        CuentaProyeccionPort {

    static final String CONOCIMIENTO = "base_conocimiento_turistico";
    static final String ITINERARIOS = "itinerarios_sugeridos";
    static final String USO = "uso_diario";
    static final String CUENTAS = "cuentas";

    private final MongoTemplate mongo;

    public ChatbotMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    // ------------------------------------------------------------------ base de conocimientos (RF05)

    @Override
    public List<EntradaConocimiento> relevantes(List<String> palabras, int limite) {
        if (palabras.isEmpty()) {
            return List.of();
        }
        List<Criteria> alguna = new ArrayList<>();
        for (String palabra : palabras) {
            Pattern patron = Pattern.compile(Pattern.quote(palabra), Pattern.CASE_INSENSITIVE);
            alguna.add(Criteria.where("contenido").regex(patron));
            alguna.add(Criteria.where("categoria").regex(patron));
        }
        Query consulta = Query.query(new Criteria().orOperator(alguna))
                .with(Sort.by(Sort.Order.desc("fechaActualizacion"))).limit(limite);
        return mongo.find(consulta, Document.class, CONOCIMIENTO).stream().map(ChatbotMongoAdapter::entrada).toList();
    }

    @Override
    public List<EntradaConocimiento> listar(int pagina, int tamano) {
        Query consulta = new Query().with(Sort.by(Sort.Order.asc("categoria"), Sort.Order.desc("fechaActualizacion")))
                .skip((long) pagina * tamano).limit(tamano);
        return mongo.find(consulta, Document.class, CONOCIMIENTO).stream().map(ChatbotMongoAdapter::entrada).toList();
    }

    @Override
    public long contar() {
        return mongo.count(new Query(), CONOCIMIENTO);
    }

    @Override
    public Optional<EntradaConocimiento> buscar(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongo.findById(new ObjectId(id), Document.class, CONOCIMIENTO))
                .map(ChatbotMongoAdapter::entrada);
    }

    @Override
    public EntradaConocimiento guardar(EntradaConocimiento entrada) {
        Document documento = new Document("contenido", entrada.contenido()).append("categoria", entrada.categoria())
                .append("fechaActualizacion", Date.from(entrada.fechaActualizacion()));
        if (entrada.id() != null) {
            documento.append("_id", new ObjectId(entrada.id()));
        }
        Document guardado = mongo.save(documento, CONOCIMIENTO);
        return entrada(guardado);
    }

    @Override
    public boolean eliminar(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return false;
        }
        return mongo.remove(Query.query(Criteria.where("_id").is(new ObjectId(id))), CONOCIMIENTO).getDeletedCount() == 1;
    }

    private static EntradaConocimiento entrada(Document d) {
        return new EntradaConocimiento(d.getObjectId("_id").toHexString(), d.getString("contenido"),
                d.getString("categoria"), d.getDate("fechaActualizacion").toInstant());
    }

    // ------------------------------------------------------------------ itinerarios (RF35)

    @Override
    public List<Itinerario> listar(UUID turista) {
        Query consulta = Query.query(Criteria.where("turistaId").is(turista.toString()))
                .with(Sort.by(Sort.Order.desc("fechaActualizacion"), Sort.Order.desc("_id")));
        return mongo.find(consulta, Document.class, ITINERARIOS).stream().map(ChatbotMongoAdapter::itinerario).toList();
    }

    @Override
    public Optional<Itinerario> buscar(UUID turista, String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where("_id").is(new ObjectId(id))
                .and("turistaId").is(turista.toString())), Document.class, ITINERARIOS)).map(ChatbotMongoAdapter::itinerario);
    }

    @Override
    public long contar(UUID turista) {
        return mongo.count(Query.query(Criteria.where("turistaId").is(turista.toString())), ITINERARIOS);
    }

    @Override
    public Itinerario guardar(Itinerario itinerario) {
        List<Document> items = itinerario.items().stream().map(i -> new Document("orden", i.orden())
                .append("tipo", i.tipo()).append("refId", i.refId()).append("tituloActividad", i.tituloActividad())
                .append("diaSugerido", i.diaSugerido())).toList();
        Document documento = new Document("turistaId", itinerario.turistaId().toString())
                .append("nombre", itinerario.nombre()).append("items", items)
                .append("fechaCreacion", Date.from(itinerario.fechaCreacion()))
                .append("fechaActualizacion", Date.from(itinerario.fechaActualizacion()));
        if (itinerario.id() != null) {
            documento.append("_id", new ObjectId(itinerario.id()));
        }
        return itinerario(mongo.save(documento, ITINERARIOS));
    }

    @Override
    public boolean eliminar(UUID turista, String id) {
        if (id == null || !ObjectId.isValid(id)) {
            return false;
        }
        return mongo.remove(Query.query(Criteria.where("_id").is(new ObjectId(id))
                .and("turistaId").is(turista.toString())), ITINERARIOS).getDeletedCount() == 1;
    }

    @SuppressWarnings("unchecked")
    private static Itinerario itinerario(Document d) {
        List<Itinerario.Item> items = ((List<Document>) d.get("items")).stream()
                .map(i -> new Itinerario.Item(i.getInteger("orden"), i.getString("tipo"), i.getString("refId"),
                        i.getString("tituloActividad"), i.getInteger("diaSugerido", 1))).toList();
        return new Itinerario(d.getObjectId("_id").toHexString(), UUID.fromString(d.getString("turistaId")),
                d.getString("nombre"), items, d.getDate("fechaCreacion").toInstant(),
                d.getDate("fechaActualizacion").toInstant());
    }

    // ------------------------------------------------------------------ uso diario (DEC-297)

    /**
     * Un solo {@code findAndModify} con upsert sobre {turistaId, dia, mensajes < máximo}: dos mensajes simultáneos no
     * pasan el tope. Si ya llegó, el upsert choca con el índice único (turistaId, dia) y se responde vacío.
     */
    @Override
    public OptionalLong consumir(UUID turista, LocalDate dia, int maximo) {
        Query bajoElTope = Query.query(Criteria.where("turistaId").is(turista.toString()).and("dia").is(dia.toString())
                .and("mensajes").lt(maximo));
        try {
            Document resultado = mongo.findAndModify(bajoElTope, new Update().inc("mensajes", 1),
                    FindAndModifyOptions.options().upsert(true).returnNew(true), Document.class, USO);
            return resultado == null ? OptionalLong.empty() : OptionalLong.of(resultado.getInteger("mensajes"));
        } catch (DuplicateKeyException yaLlego) {
            return OptionalLong.empty();
        }
    }

    // ------------------------------------------------------------------ cuentas (DEC-280)

    @Override
    public void actualizarSiMayor(UUID idUsuario, String rol, String estadoCuenta, long version) {
        OptionalLong conocida = versionDe(idUsuario);
        if (conocida.isPresent() && conocida.getAsLong() >= version) {
            return;
        }
        mongo.upsert(Query.query(Criteria.where("_id").is(idUsuario.toString())),
                new Update().set("rol", rol).set("estadoCuenta", estadoCuenta).set("version", version), CUENTAS);
    }

    @Override
    public OptionalLong versionDe(UUID idUsuario) {
        Document cuenta = mongo.findById(idUsuario.toString(), Document.class, CUENTAS);
        if (cuenta == null || cuenta.get("version") == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(((Number) cuenta.get("version")).longValue());
    }
}
