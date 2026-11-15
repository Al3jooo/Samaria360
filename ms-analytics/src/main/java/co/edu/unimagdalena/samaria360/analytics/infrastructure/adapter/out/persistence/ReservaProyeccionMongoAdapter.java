package co.edu.unimagdalena.samaria360.analytics.infrastructure.adapter.out.persistence;

import co.edu.unimagdalena.samaria360.analytics.application.port.out.ReservaProyeccionPort;
import co.edu.unimagdalena.samaria360.analytics.domain.model.ReservaProyectada;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Colección técnica {@code reservas} de analytics_db (DEC-292): {@code _id} = id de la reserva. Un evento posterior
 * cambia el estado y conserva lo que ya se sabía (la fecha de inicio y las personas no viajan en
 * {@code ReservaCompletada}). Sin datos personales: solo identificadores.
 */
@Component
public class ReservaProyeccionMongoAdapter implements ReservaProyeccionPort {

    static final String COLECCION = "reservas";

    private final MongoTemplate mongo;

    public ReservaProyeccionMongoAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void guardar(ReservaProyectada reserva) {
        Update datos = new Update()
                .set("idTurista", reserva.idTurista().toString())
                .set("idPrestador", reserva.idPrestador().toString())
                .set("idServicio", reserva.idServicio())
                .set("nombreServicio", reserva.nombreServicio())
                .set("estado", reserva.estado().name())
                .set("actualizadaEn", Date.from(reserva.actualizadaEn()));
        if (reserva.fechaInicio() != null) {
            datos.set("fechaInicio", Date.from(reserva.fechaInicio()));
        }
        if (reserva.personas() > 0) {
            datos.set("personas", reserva.personas());
        }
        if (reserva.estado() == ReservaProyectada.EstadoReserva.Confirmada) {
            datos.set("confirmadaEn", Date.from(reserva.actualizadaEn()));
        }
        mongo.upsert(Query.query(Criteria.where("_id").is(reserva.idReserva().toString())), datos, COLECCION);
    }

    @Override
    public java.util.Optional<ReservaProyectada> completadaDe(java.util.UUID turista, String idServicio) {
        org.bson.Document d = mongo.findOne(Query.query(Criteria.where("idTurista").is(turista.toString())
                        .and("idServicio").is(idServicio)
                        .and("estado").is(ReservaProyectada.EstadoReserva.Completada.name()))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                        "actualizadaEn")), org.bson.Document.class, COLECCION);
        if (d == null) {
            return java.util.Optional.empty();
        }
        Date inicio = d.getDate("fechaInicio");
        Number personas = (Number) d.get("personas");
        return java.util.Optional.of(new ReservaProyectada(java.util.UUID.fromString(d.getString("_id")), turista,
                java.util.UUID.fromString(d.getString("idPrestador")), idServicio, d.getString("nombreServicio"),
                inicio == null ? null : inicio.toInstant(), personas == null ? 0 : personas.intValue(),
                ReservaProyectada.EstadoReserva.Completada, d.getDate("actualizadaEn").toInstant()));
    }
}
