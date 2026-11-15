package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.FiltroCalificacionesPrestador;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.Pagina;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.ResumenServicio;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.CalificacionRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.domain.model.Calificacion;
import co.edu.unimagdalena.samaria360.analytics.domain.model.PromedioCalificacion;
import co.edu.unimagdalena.samaria360.analytics.domain.model.TipoEntidad;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Colección {@code calificaciones} de analytics_db (A002; DEC-300). Índice único {@code (turistaId, tipoEntidad,
 * idEntidad)}: el caso de uso consulta antes de insertar y la infraestructura traduce la llave duplicada de dos envíos
 * simultáneos a {@code CALIFICACION_DUPLICADA}.
 */
@Component
public class CalificacionMongoAdapter implements CalificacionRepositoryPort {

    static final String COLECCION = "calificaciones";
    private static final String VISIBLE = Calificacion.Estado.Visible.name();

    private final MongoTemplate mongo;

    public CalificacionMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean existe(UUID turista, TipoEntidad tipo, String idEntidad) {
        return mongo.exists(deTuristaQuery(turista, tipo, idEntidad), COLECCION);
    }

    @Override
    public Optional<Calificacion> deTurista(UUID turista, TipoEntidad tipo, String idEntidad) {
        return Optional.ofNullable(mongo.findOne(deTuristaQuery(turista, tipo, idEntidad), Document.class, COLECCION))
                .map(CalificacionMongoAdapter::aDominio);
    }

    @Override
    public Calificacion insertar(Calificacion c) {
        ObjectId id = new ObjectId();
        Document documento = new Document("_id", id)
                .append("tipoEntidad", c.tipoEntidad().name())
                .append("idEntidad", c.idEntidad())
                .append("turistaId", c.turistaId().toString())
                .append("estrellas", c.estrellas())
                .append("comentario", c.comentario())
                .append("estado", c.estado().name())
                .append("fecha", Date.from(c.fecha()));
        if (c.idPrestador() != null) {
            documento.append("idPrestador", c.idPrestador().toString());
        }
        if (c.nombreEntidad() != null) {
            documento.append("nombreEntidad", c.nombreEntidad());
        }
        mongo.insert(documento, COLECCION);
        return c.conId(id.toHexString());
    }

    @Override
    public Optional<Calificacion> buscar(String id) {
        return Optional.ofNullable(mongo.findById(new ObjectId(id), Document.class, COLECCION))
                .map(CalificacionMongoAdapter::aDominio);
    }

    @Override
    public boolean cambiarEstado(String id, Calificacion.Estado esperado, Calificacion.Estado nuevo, String motivo,
                                 Instant fecha) {
        return mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(id)).and("estado").is(esperado.name())),
                new Update().set("estado", nuevo.name()).set("motivoModeracion", motivo)
                        .set("fechaModeracion", Date.from(fecha)), COLECCION).getModifiedCount() == 1;
    }

    @Override
    public boolean eliminar(String id) {
        return mongo.remove(Query.query(Criteria.where("_id").is(new ObjectId(id))), COLECCION).getDeletedCount() == 1;
    }

    @Override
    public long[] sumaYCantidadVisibles(TipoEntidad tipo, String idEntidad) {
        return sumaYCantidad(new Document("tipoEntidad", tipo.name()).append("idEntidad", idEntidad)
                .append("estado", VISIBLE));
    }

    @Override
    public Pagina<Calificacion> visibles(TipoEntidad tipo, String idEntidad, int pagina, int tamano) {
        return pagina(Criteria.where("tipoEntidad").is(tipo.name()).and("idEntidad").is(idEntidad).and("estado")
                .is(VISIBLE), Sort.Direction.DESC, pagina, tamano);
    }

    @Override
    public Pagina<Calificacion> delPrestador(FiltroCalificacionesPrestador filtro, int pagina, int tamano) {
        return pagina(criteriosPrestador(filtro), Sort.Direction.DESC, pagina, tamano);
    }

    @Override
    public long[] sumaYCantidadDelPrestador(FiltroCalificacionesPrestador filtro) {
        Document coincidencia = new Document("idPrestador", filtro.prestador().toString()).append("estado", VISIBLE);
        if (filtro.idServicio() != null) {
            coincidencia.append("idEntidad", filtro.idServicio());
        }
        Document fecha = new Document();
        if (filtro.desde() != null) {
            fecha.append("$gte", Date.from(filtro.desde()));
        }
        if (filtro.hasta() != null) {
            fecha.append("$lt", Date.from(filtro.hasta()));
        }
        if (!fecha.isEmpty()) {
            coincidencia.append("fecha", fecha);
        }
        return sumaYCantidad(coincidencia);
    }

    @Override
    public List<ResumenServicio> serviciosDelPrestador(UUID prestador) {
        List<Document> etapas = List.of(
                new Document("$match", new Document("idPrestador", prestador.toString()).append("estado", VISIBLE)),
                new Document("$group", new Document("_id", "$idEntidad")
                        .append("nombre", new Document("$last", "$nombreEntidad"))
                        .append("suma", new Document("$sum", "$estrellas"))
                        .append("cantidad", new Document("$sum", 1))),
                new Document("$sort", new Document("nombre", 1).append("_id", 1)));
        List<ResumenServicio> resumen = new ArrayList<>();
        for (Document d : agregar(etapas)) {
            long suma = ((Number) d.get("suma")).longValue();
            long cantidad = ((Number) d.get("cantidad")).longValue();
            String id = d.getString("_id");
            resumen.add(new ResumenServicio(id, d.getString("nombre"),
                    PromedioCalificacion.de(TipoEntidad.servicio, id, suma, cantidad).promedio(), cantidad));
        }
        return resumen;
    }

    @Override
    public Pagina<Calificacion> paraModerar(Calificacion.Estado estado, int pagina, int tamano) {
        Criteria criterios = estado == null ? new Criteria() : Criteria.where("estado").is(estado.name());
        return pagina(criterios, Sort.Direction.DESC, pagina, tamano);
    }

    private Pagina<Calificacion> pagina(Criteria criterios, Sort.Direction direccion, int pagina, int tamano) {
        Query consulta = Query.query(criterios);
        long total = mongo.count(consulta, COLECCION);
        consulta.with(Sort.by(direccion, "fecha").and(Sort.by(direccion, "_id")))
                .skip((long) pagina * tamano).limit(tamano);
        List<Calificacion> elementos = mongo.find(consulta, Document.class, COLECCION).stream()
                .map(CalificacionMongoAdapter::aDominio).toList();
        return new Pagina<>(elementos, pagina, tamano, total);
    }

    private static Criteria criteriosPrestador(FiltroCalificacionesPrestador filtro) {
        Criteria criterios = Criteria.where("idPrestador").is(filtro.prestador().toString()).and("estado").is(VISIBLE);
        if (filtro.idServicio() != null) {
            criterios.and("idEntidad").is(filtro.idServicio());
        }
        if (filtro.desde() != null || filtro.hasta() != null) {
            Criteria fecha = criterios.and("fecha");
            if (filtro.desde() != null) {
                fecha.gte(Date.from(filtro.desde()));
            }
            if (filtro.hasta() != null) {
                fecha.lt(Date.from(filtro.hasta()));
            }
        }
        return criterios;
    }

    private long[] sumaYCantidad(Document coincidencia) {
        List<Document> etapas = List.of(new Document("$match", coincidencia),
                new Document("$group", new Document("_id", null)
                        .append("suma", new Document("$sum", "$estrellas"))
                        .append("cantidad", new Document("$sum", 1))));
        List<Document> resultados = agregar(etapas);
        Document resultado = resultados.isEmpty() ? null : resultados.get(0);
        if (resultado == null) {
            return new long[]{0, 0};
        }
        return new long[]{((Number) resultado.get("suma")).longValue(), ((Number) resultado.get("cantidad")).longValue()};
    }

    /**
     * Por {@code MongoTemplate} (no por la colección del driver): así la agregación usa la sesión de la transacción y
     * ve las escrituras del mismo caso de uso al recalcular el promedio.
     */
    private List<Document> agregar(List<Document> etapas) {
        List<AggregationOperation> operaciones = etapas.stream()
                .<AggregationOperation>map(etapa -> contexto -> etapa).toList();
        return mongo.aggregate(Aggregation.newAggregation(operaciones), COLECCION, Document.class).getMappedResults();
    }

    private static Query deTuristaQuery(UUID turista, TipoEntidad tipo, String idEntidad) {
        return Query.query(Criteria.where("turistaId").is(turista.toString()).and("tipoEntidad").is(tipo.name())
                .and("idEntidad").is(idEntidad));
    }

    static Calificacion aDominio(Document d) {
        String prestador = d.getString("idPrestador");
        Date moderacion = d.getDate("fechaModeracion");
        return new Calificacion(d.getObjectId("_id").toHexString(), TipoEntidad.valueOf(d.getString("tipoEntidad")),
                d.getString("idEntidad"), UUID.fromString(d.getString("turistaId")),
                prestador == null ? null : UUID.fromString(prestador), d.getString("nombreEntidad"),
                d.getInteger("estrellas"), d.getString("comentario"),
                Calificacion.Estado.valueOf(d.getString("estado")), d.getString("motivoModeracion"),
                d.getDate("fecha").toInstant(), moderacion == null ? null : moderacion.toInstant());
    }
}
