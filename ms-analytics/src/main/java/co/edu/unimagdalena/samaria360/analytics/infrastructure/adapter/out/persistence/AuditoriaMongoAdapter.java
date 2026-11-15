package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.analytics.application.port.in.FiltroAuditoria;
import co.edu.unimagdalena.samaria360.analytics.application.port.in.PaginaAuditoria;
import co.edu.unimagdalena.samaria360.analytics.application.port.out.AuditoriaRepositoryPort;
import co.edu.unimagdalena.samaria360.analytics.domain.model.AccionAuditada;
import co.edu.unimagdalena.samaria360.analytics.domain.model.RegistroAuditoria;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Colección {@code logs_auditoria} (Entregable 2, §2.4; RF13): {@code usuarioId}, {@code accion}, {@code detalle} y
 * {@code fecha}, más {@code idEvento} (ampliación, DEC-292: índice único para no duplicar una entrada).
 */
@Component
public class AuditoriaMongoAdapter implements AuditoriaRepositoryPort {

    static final String COLECCION = "logs_auditoria";

    private final MongoTemplate mongo;

    public AuditoriaMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void registrar(RegistroAuditoria registro) {
        if (mongo.exists(Query.query(Criteria.where("idEvento").is(registro.idEvento().toString())), COLECCION)) {
            return;
        }
        mongo.insert(new Document("idEvento", registro.idEvento().toString())
                .append("usuarioId", registro.usuarioId().toString())
                .append("accion", registro.accion().name())
                .append("detalle", registro.detalle())
                .append("fecha", Date.from(registro.fecha())), COLECCION);
    }

    @Override
    public PaginaAuditoria listar(FiltroAuditoria filtro, int pagina, int tamano) {
        Criteria criterio = new Criteria();
        List<Criteria> condiciones = new ArrayList<>();
        if (filtro.accion() != null) {
            condiciones.add(Criteria.where("accion").is(filtro.accion().name()));
        }
        if (filtro.usuario() != null) {
            condiciones.add(Criteria.where("usuarioId").is(filtro.usuario().toString()));
        }
        if (filtro.desde() != null) {
            condiciones.add(Criteria.where("fecha").gte(Date.from(filtro.desde())));
        }
        if (filtro.hasta() != null) {
            condiciones.add(Criteria.where("fecha").lt(Date.from(filtro.hasta())));
        }
        if (!condiciones.isEmpty()) {
            criterio.andOperator(condiciones);
        }
        Query consulta = Query.query(criterio);
        long total = mongo.count(consulta, COLECCION);
        consulta.with(Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("_id"))).skip((long) pagina * tamano)
                .limit(tamano);
        List<RegistroAuditoria> elementos = mongo.find(consulta, Document.class, COLECCION).stream()
                .map(d -> new RegistroAuditoria(UUID.fromString(d.getString("idEvento")),
                        UUID.fromString(d.getString("usuarioId")), AccionAuditada.desdeTexto(d.getString("accion")),
                        d.getString("detalle"), d.getDate("fecha").toInstant()))
                .toList();
        return new PaginaAuditoria(elementos, pagina, tamano, total);
    }
}
